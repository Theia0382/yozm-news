-- event 모듈 — 사건 정리(병합 · 닫기)와 재판정의 연 사건 표지.
--
-- 사건 상태에 합쳐진 사건(MERGED)을 더하고, 합쳐진 사건은 남는 사건을 바로 가리킨다(사슬 없음). 닫기는 마지막으로 기사가 붙은 시각과
-- 연 시각으로 판정하고, 병합 회차는 지난 확인 뒤 바뀐 사건만 다시 본다(revision). 배정은 그 기사가 사건을 열었는지를 남긴다.

-- V17 의 상태 제약은 컬럼 제약이라 이름이 자동으로 붙었다
ALTER TABLE events DROP CONSTRAINT events_status_check;
ALTER TABLE events
  ADD CONSTRAINT events_status_check CHECK (status IN ('OPEN', 'CLOSED', 'MERGED')),
  -- 합쳐진 사건이 가리키는 남는 사건 — MERGED 일 때만 있다
  ADD COLUMN merged_into                BIGINT       REFERENCES events,
  -- 열림이 끝난 시각 — 닫았거나 합쳐졌을 때
  ADD COLUMN closed_at                  TIMESTAMPTZ,
  -- 마지막으로 기사가 붙은 시각 — 닫기의 기준
  ADD COLUMN last_joined_at             TIMESTAMPTZ,
  -- 사건을 고칠 때마다 하나씩 는다 — 병합 회차가 바뀐 사건을 고른다
  ADD COLUMN revision                   BIGINT       NOT NULL DEFAULT 1,
  -- 병합 회차가 마지막으로 확인한 revision 과 그때의 기준
  ADD COLUMN merge_checked_revision     BIGINT       NOT NULL DEFAULT 0,
  ADD COLUMN merge_checked_criteria_id  BIGINT       REFERENCES event_assignment_criteria,
  -- 닫을 때 남긴 대표 기사 — article 모듈의 기사를 가리키지만 FK 를 두지 않는다
  ADD COLUMN representative_article_id  BIGINT;

-- 있는 사건 — 배정 시각의 최대(재판정의 다시 적기도 배정 시각을 고쳐 실제보다 늦을 수 있다), 배정이 없으면 연 시각
UPDATE events e
SET last_joined_at = coalesce((SELECT max(a.assigned_at) FROM event_articles a WHERE a.event_id = e.id), e.created_at);

ALTER TABLE events
  ALTER COLUMN last_joined_at SET NOT NULL,
  ADD CHECK ((status = 'MERGED') = (merged_into IS NOT NULL)),
  ADD CHECK ((status = 'OPEN') = (closed_at IS NULL)),
  ADD CHECK (merged_into IS NULL OR merged_into <> id);

CREATE INDEX events_merge_due_idx ON events (id) WHERE status = 'OPEN' AND merge_checked_revision < revision;
CREATE INDEX events_merged_into_idx ON events (merged_into) WHERE merged_into IS NOT NULL;

-- 배정 — 이 기사가 판정으로 이 배정의 사건을 열었다(처음 판정의 새 사건 · 재판정의 떼어 내기). 병합으로 사건이 바뀌어도 그대로다.
-- 있는 배정은 병합 전이라 "지금 사건이 저장 후보에 없으면 연 사건"이 맞다 — 붙이기 · 옮기기의 사건은 저장 후보에서만 나온다
ALTER TABLE event_articles ADD COLUMN opened BOOLEAN;
UPDATE event_articles a
SET opened = NOT EXISTS (
  SELECT 1 FROM event_article_candidates c WHERE c.article_id = a.article_id AND c.event_id = a.event_id);
ALTER TABLE event_articles ALTER COLUMN opened SET NOT NULL;

-- 병합 판정 기록 — 띠에 든 사건 쌍의 판정 확률. 순서 없는 쌍(작은 ID, 큰 ID)이고, 사건을 지울 때 함께 지운다(FK 없음)
CREATE TABLE event_merge_judgements (
  event_low    BIGINT            NOT NULL,
  event_high   BIGINT            NOT NULL,
  probability  DOUBLE PRECISION  NOT NULL CHECK (probability >= 0 AND probability <= 1),
  judged_at    TIMESTAMPTZ       NOT NULL,
  PRIMARY KEY (event_low, event_high),
  CHECK (event_low < event_high)
);
CREATE INDEX event_merge_judgements_high_idx ON event_merge_judgements (event_high);
