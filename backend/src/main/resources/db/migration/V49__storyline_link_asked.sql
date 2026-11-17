-- storyline 모듈 — 잇기의 띠 판정(storyline-R36).
--
-- 잇기 기준 이력은 기준 점수 하나에서 아래 기준 · 위 기준 · 확률 기준으로 바뀐다. 옛 행은 띠가 빈 기준이었다 — 기준 점수가
-- 위 기준이자 아래 기준이고, 확률 기준은 쓰지 않았다(NULL). 새 행은 언제나 확률 기준이 있다.
ALTER TABLE storyline_linking_criteria RENAME COLUMN threshold TO upper;
ALTER TABLE storyline_linking_criteria ADD COLUMN lower DOUBLE PRECISION;
UPDATE storyline_linking_criteria SET lower = upper;
ALTER TABLE storyline_linking_criteria ALTER COLUMN lower SET NOT NULL;
ALTER TABLE storyline_linking_criteria ADD COLUMN probability DOUBLE PRECISION;
ALTER TABLE storyline_linking_criteria DROP CONSTRAINT storyline_linking_criteria_window_seconds_threshold_key;
ALTER TABLE storyline_linking_criteria DROP CONSTRAINT storyline_linking_criteria_threshold_check;
ALTER TABLE storyline_linking_criteria
  ADD CONSTRAINT storyline_linking_criteria_band CHECK (lower > 0 AND lower <= upper AND upper <= 1),
  ADD CONSTRAINT storyline_linking_criteria_probability CHECK (probability > 0 AND probability <= 1),
  ADD CONSTRAINT storyline_linking_criteria_probability_used CHECK (probability IS NOT NULL OR lower = upper),
  ADD CONSTRAINT storyline_linking_criteria_values UNIQUE NULLS NOT DISTINCT (window_seconds, lower, upper, probability);

-- 소속의 판정 근거 — 띠 안에서 판정(Jev)으로 정한 소속은 그 확률과 판정 원본 ID 를 갖는다. 위 기준 이상으로 붙은 소속 ·
-- 옮긴 소속(MERGE)은 없다. judgement_id 는 evaluation 의 흐름 소속 판정 원본을 가리키지만 FK 를 두지 않는다
ALTER TABLE storyline_members ADD COLUMN probability DOUBLE PRECISION;
ALTER TABLE storyline_members ADD COLUMN judgement_id BIGINT;
ALTER TABLE storyline_members
  ADD CONSTRAINT storyline_members_judged CHECK ((probability IS NULL) = (judgement_id IS NULL)),
  ADD CONSTRAINT storyline_members_probability CHECK (probability >= 0 AND probability <= 1),
  ADD CONSTRAINT storyline_members_merge_unjudged CHECK (linked_by <> 'MERGE' OR probability IS NULL);

-- 붙이기 띠의 물은 기록 — (판정할 사건, 후보 스토리라인) 마다 한 행. 판정의 확률은 이 기록이 정본이다.
-- probability · judgement_id 가 없으면 확률을 얻지 못한 것이다(입력 거부 · 서술이 모두 빠져 묻지 못함). 크기는 물었을 때의 값이다
CREATE TABLE storyline_link_asked (
  event_id          BIGINT            NOT NULL,
  storyline_id      BIGINT            NOT NULL REFERENCES storylines ON DELETE CASCADE,
  probability       DOUBLE PRECISION  CHECK (probability >= 0 AND probability <= 1),
  judgement_id      BIGINT,
  event_articles    INTEGER           NOT NULL CHECK (event_articles >= 1),
  storyline_events  INTEGER           NOT NULL CHECK (storyline_events >= 1),
  asked_at          TIMESTAMPTZ       NOT NULL,
  PRIMARY KEY (event_id, storyline_id),
  CHECK ((probability IS NULL) = (judgement_id IS NULL))
);
CREATE INDEX storyline_link_asked_storyline_idx ON storyline_link_asked (storyline_id);

-- 짝 띠의 물은 기록 — (판정할 사건, 이웃 사건) 마다 한 행. 방향이 있다 — 판정할 사건이 앞이다
CREATE TABLE storyline_pair_asked (
  event_id          BIGINT            NOT NULL,
  partner_id        BIGINT            NOT NULL,
  probability       DOUBLE PRECISION  CHECK (probability >= 0 AND probability <= 1),
  judgement_id      BIGINT,
  event_articles    INTEGER           NOT NULL CHECK (event_articles >= 1),
  partner_articles  INTEGER           NOT NULL CHECK (partner_articles >= 1),
  asked_at          TIMESTAMPTZ       NOT NULL,
  PRIMARY KEY (event_id, partner_id),
  CHECK (event_id <> partner_id),
  CHECK ((probability IS NULL) = (judgement_id IS NULL))
);
CREATE INDEX storyline_pair_asked_partner_idx ON storyline_pair_asked (partner_id);

-- 잇기 대기의 판정 가능 시각 — 평가기 일시 실패면 미룬다. 없으면 바로 판정한다
ALTER TABLE storyline_linking_queue ADD COLUMN retry_at TIMESTAMPTZ;
