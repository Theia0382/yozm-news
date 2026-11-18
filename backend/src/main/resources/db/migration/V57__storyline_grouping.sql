-- 나란한 흐름을 한 상위 스토리라인 아래 묶는다 — docs/specs/storyline.md §6.
--
-- 관계의 맺은 쌍: 상위를 열 때 맺은 포함 고리는 그 고리의 두 끝이 아니라 나란한 흐름으로 판정된 쌍의 판정이 맺는다. 두 열이 모두 있을 때만
-- 그 표지가 있는 고리다 — 맺은 쌍의 한쪽이 지워지면 그 열이 비어 보통 관계가 된다.
ALTER TABLE storyline_relations
  ADD COLUMN grouped_low_id   BIGINT REFERENCES storylines ON DELETE SET NULL,
  ADD COLUMN grouped_high_id  BIGINT REFERENCES storylines ON DELETE SET NULL,
  ADD CONSTRAINT storyline_relations_grouped_ordered CHECK (grouped_low_id < grouped_high_id);

-- 넘겨 물을 쌍: 점수 · 사건 공유와 무관하게 관계 회차가 물을 쌍이다. 물으면(또는 물을 수 없으면) 지운다.
CREATE TABLE storyline_relation_referrals (
  low_id       BIGINT       NOT NULL REFERENCES storylines ON DELETE CASCADE,
  high_id      BIGINT       NOT NULL REFERENCES storylines ON DELETE CASCADE,
  referred_at  TIMESTAMPTZ  NOT NULL,
  PRIMARY KEY (low_id, high_id),
  CONSTRAINT storyline_relation_referrals_ordered CHECK (low_id < high_id)
);
CREATE INDEX storyline_relation_referrals_high_idx ON storyline_relation_referrals (high_id);

-- 소속을 정한 것에 상위를 열 때의 평가(GROUP)를 더한다 — 붙이기 · 짝처럼 점수 · 기준을 갖는다.
ALTER TABLE storyline_members DROP CONSTRAINT storyline_members_linked_by_check;
ALTER TABLE storyline_members ADD CONSTRAINT storyline_members_linked_by_check
  CHECK (linked_by IN ('RULE', 'PAIR', 'MERGE', 'GROUP'));
