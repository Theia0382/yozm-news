-- storyline 모듈 — 스토리라인 사이 관계 · 관계 기준 이력 · 물은 기록 · 관계 대기(docs/specs/storyline.md §6 · storyline-R25 ~ R32).
--
-- judgement_id 는 evaluation 모듈의 관계 판정 원본(flow_relation_judgements)을 가리키지만 FK 를 두지 않는다 — 모듈 사이는 ID 로만
-- 참조하고, 원본은 보존 기간이 지나 먼저 지워질 수 있다.

-- 관계 기준 이력 — 회차가 쓴 기준 값마다 한 행. 관계와 물은 기록이 어떤 기준으로 판정됐는지 가리킨다
CREATE TABLE storyline_relation_criteria (
  id               BIGSERIAL         PRIMARY KEY,
  probability      DOUBLE PRECISION  NOT NULL CHECK (probability > 0 AND probability <= 1),
  one_matter       DOUBLE PRECISION  NOT NULL CHECK (one_matter >= 0 AND one_matter <= 1),
  min_events       INTEGER           NOT NULL CHECK (min_events >= 2),
  candidate_score  DOUBLE PRECISION  NOT NULL CHECK (candidate_score > 0 AND candidate_score <= 1),
  window_seconds   BIGINT            NOT NULL CHECK (window_seconds > 0),
  created_at       TIMESTAMPTZ       NOT NULL,
  UNIQUE (probability, one_matter, min_events, candidate_score, window_seconds)
);

-- 관계 — 포함(CONTAINS: source 가 상위, target 이 하위)과 이어짐(FOLLOWS: source 가 target 을 낳았다).
-- 순환은 제약이 아니라 판정이 모듈 잠금 안에서 확인한다
CREATE TABLE storyline_relations (
  id            BIGSERIAL         PRIMARY KEY,
  kind          TEXT              NOT NULL CHECK (kind IN ('CONTAINS', 'FOLLOWS')),
  source_id     BIGINT            NOT NULL REFERENCES storylines ON DELETE CASCADE,
  target_id     BIGINT            NOT NULL REFERENCES storylines ON DELETE CASCADE,
  probability   DOUBLE PRECISION  NOT NULL CHECK (probability >= 0 AND probability <= 1),
  judgement_id  BIGINT            NOT NULL,
  criteria_id   BIGINT            NOT NULL REFERENCES storyline_relation_criteria,
  linked_at     TIMESTAMPTZ       NOT NULL,
  CONSTRAINT storyline_relations_not_self CHECK (source_id <> target_id)
);
-- 한 쌍의 관계는 하나다 — 종류 · 방향과 무관
CREATE UNIQUE INDEX storyline_relations_pair_idx
  ON storyline_relations (LEAST(source_id, target_id), GREATEST(source_id, target_id));
-- 포함의 하위는 상위를 하나만 갖는다
CREATE UNIQUE INDEX storyline_relations_parent_idx ON storyline_relations (target_id) WHERE kind = 'CONTAINS';
CREATE INDEX storyline_relations_source_idx ON storyline_relations (source_id);
CREATE INDEX storyline_relations_target_idx ON storyline_relations (target_id);

-- 물은 기록 — 쌍마다 마지막으로 물은 결말. 다시 물을 때를 정한다(storyline-R30)
CREATE TABLE storyline_relation_asked (
  low_id          BIGINT       NOT NULL REFERENCES storylines ON DELETE CASCADE,
  high_id         BIGINT       NOT NULL REFERENCES storylines ON DELETE CASCADE,
  smaller_events  INTEGER      NOT NULL CHECK (smaller_events >= 1),
  outcome         TEXT         NOT NULL CHECK (outcome IN ('LINKED', 'NOT_RELATED', 'LOW_PROBABILITY', 'NOT_ONE_MATTER',
                                                           'CYCLE', 'SECOND_PARENT', 'NOTHING_TO_ASK', 'REJECTED')),
  -- 평가기를 부르지 않았으면 없다
  judgement_id    BIGINT,
  criteria_id     BIGINT       NOT NULL REFERENCES storyline_relation_criteria,
  asked_at        TIMESTAMPTZ  NOT NULL,
  PRIMARY KEY (low_id, high_id),
  CONSTRAINT storyline_relation_asked_ordered CHECK (low_id < high_id)
);
CREATE INDEX storyline_relation_asked_high_idx ON storyline_relation_asked (high_id);

-- 관계 대기 — 스토리라인마다 한 행(storyline-R26 · R31)
CREATE TABLE storyline_relation_queue (
  storyline_id      BIGINT       PRIMARY KEY REFERENCES storylines ON DELETE CASCADE,
  notifications     INTEGER      NOT NULL DEFAULT 0 CHECK (notifications >= 0),
  failures          INTEGER      NOT NULL DEFAULT 0 CHECK (failures >= 0),
  enqueued_at       TIMESTAMPTZ  NOT NULL,
  lease_token       UUID,
  lease_expires_at  TIMESTAMPTZ,
  CHECK ((lease_token IS NULL) = (lease_expires_at IS NULL))
);
