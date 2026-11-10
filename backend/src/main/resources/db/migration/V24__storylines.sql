-- storyline 모듈 — 잇기 기준 이력 · 공유 스토리라인 · 스토리라인 핵심 말 · 소속 · 잇기 대기(docs/specs/storyline.md §6).
--
-- event_id · representative_event_id 는 event 모듈의 events 를 가리키지만 FK 를 두지 않는다 — 모듈 사이는 ID 로만 참조한다.

-- 잇기 기준 이력 — 회차가 쓴 기준 값마다 한 행. 소속이 어떤 기준으로 판정됐는지 가리킨다(storyline-R14)
CREATE TABLE storyline_linking_criteria (
  id              BIGSERIAL         PRIMARY KEY,
  window_seconds  BIGINT            NOT NULL CHECK (window_seconds > 0),
  threshold       DOUBLE PRECISION  NOT NULL CHECK (threshold > 0 AND threshold <= 1),
  created_at      TIMESTAMPTZ       NOT NULL,
  UNIQUE (window_seconds, threshold)
);

-- 공유 스토리라인과 그 표현 — 중심은 구성 사건 중심의 기사 수 가중 평균이고, 같은 모델 버전끼리만 비교한다(storyline-R7)
CREATE TABLE storylines (
  id                       BIGSERIAL    PRIMARY KEY,
  model_version            TEXT         NOT NULL,
  centroid                 halfvec      NOT NULL,
  article_count            INTEGER      NOT NULL CHECK (article_count >= 1),
  first_article_at         TIMESTAMPTZ  NOT NULL,
  last_article_at          TIMESTAMPTZ  NOT NULL,
  representative_event_id  BIGINT       NOT NULL,
  created_at               TIMESTAMPTZ  NOT NULL,
  updated_at               TIMESTAMPTZ  NOT NULL,
  CHECK (first_article_at <= last_article_at)
);
CREATE INDEX storylines_model_last_idx ON storylines (model_version, last_article_at);

-- 스토리라인 핵심 말 — 말마다 구성 사건 상위 핵심 말의 기사 수 합
CREATE TABLE storyline_key_terms (
  storyline_id   BIGINT   NOT NULL REFERENCES storylines ON DELETE CASCADE,
  term           TEXT     NOT NULL,
  article_count  INTEGER  NOT NULL CHECK (article_count >= 1),
  PRIMARY KEY (storyline_id, term)
);

-- 소속 — 사건 하나가 스토리라인 0개 이상에 든다. 판정한 소속은 점수 · 기준을, 옮긴 소속(MERGE)은 그것을 갖지 않는다(storyline-R14)
CREATE TABLE storyline_members (
  storyline_id      BIGINT            NOT NULL REFERENCES storylines ON DELETE CASCADE,
  event_id          BIGINT            NOT NULL,
  linked_by         TEXT              NOT NULL CHECK (linked_by IN ('RULE', 'PAIR', 'MERGE')),
  score             DOUBLE PRECISION,
  cosine            DOUBLE PRECISION,
  key_term_overlap  DOUBLE PRECISION,
  criteria_id       BIGINT            REFERENCES storyline_linking_criteria,
  linked_at         TIMESTAMPTZ       NOT NULL,
  PRIMARY KEY (storyline_id, event_id),
  CHECK (
    (linked_by = 'MERGE' AND score IS NULL AND cosine IS NULL AND key_term_overlap IS NULL AND criteria_id IS NULL)
    OR (linked_by <> 'MERGE' AND score IS NOT NULL AND cosine IS NOT NULL AND key_term_overlap IS NOT NULL
        AND criteria_id IS NOT NULL)
  )
);
CREATE INDEX storyline_members_event_idx ON storyline_members (event_id);

-- 잇기 대기 — 사건마다 한 행. 순서 키는 사건의 첫 기사 시각이고, 지워졌거나 합쳐진 사건이면 없다(맨 앞)(storyline-R2 · R3)
CREATE TABLE storyline_linking_queue (
  event_id          BIGINT       PRIMARY KEY,
  order_at          TIMESTAMPTZ,
  notifications     INTEGER      NOT NULL DEFAULT 0 CHECK (notifications >= 0),
  failures          INTEGER      NOT NULL DEFAULT 0 CHECK (failures >= 0),
  enqueued_at       TIMESTAMPTZ  NOT NULL,
  lease_token       UUID,
  lease_expires_at  TIMESTAMPTZ,
  CHECK ((lease_token IS NULL) = (lease_expires_at IS NULL))
);
CREATE INDEX storyline_linking_queue_order_idx ON storyline_linking_queue (order_at NULLS FIRST, event_id);
