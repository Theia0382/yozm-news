-- event 모듈 — 사건 · 사건 핵심 말 · 배정 기준 이력 · 배정 · 배정 후보 · 배정 대기.
--
-- article_id 는 article 모듈의 articles 를 가리키지만 FK 를 두지 않는다 — 모듈 사이는 ID 로만 참조한다.

-- 사건 — 중심 벡터는 구성 기사 벡터의 평균이고, 같은 모델 버전끼리만 비교한다. 차원은 모델마다 달라 컬럼에 정하지 않는다
CREATE TABLE events (
  id                BIGSERIAL    PRIMARY KEY,
  status            TEXT         NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
  model_version     TEXT         NOT NULL,
  centroid          halfvec      NOT NULL,
  article_count     INTEGER      NOT NULL CHECK (article_count >= 1),
  first_article_at  TIMESTAMPTZ  NOT NULL,
  last_article_at   TIMESTAMPTZ  NOT NULL,
  created_at        TIMESTAMPTZ  NOT NULL
);
CREATE INDEX events_open_idx ON events (model_version, last_article_at) WHERE status = 'OPEN';

-- 사건 핵심 말 — 말마다 그 말을 가진 구성 기사 수
CREATE TABLE event_key_terms (
  event_id       BIGINT   NOT NULL REFERENCES events ON DELETE CASCADE,
  term           TEXT     NOT NULL,
  article_count  INTEGER  NOT NULL CHECK (article_count >= 1),
  PRIMARY KEY (event_id, term)
);

-- 배정 기준 이력 — 회차가 쓴 기준 값마다 한 행. 배정이 어떤 기준으로 판정됐는지 가리킨다
CREATE TABLE event_assignment_criteria (
  id              BIGSERIAL         PRIMARY KEY,
  window_seconds  BIGINT            NOT NULL,
  upper           DOUBLE PRECISION  NOT NULL,
  lower           DOUBLE PRECISION  NOT NULL,
  store           DOUBLE PRECISION  NOT NULL,
  probability     DOUBLE PRECISION  NOT NULL,
  created_at      TIMESTAMPTZ       NOT NULL,
  UNIQUE (window_seconds, upper, lower, store, probability)
);

-- 배정 — 기사마다 한 사건
CREATE TABLE event_articles (
  article_id    BIGINT            PRIMARY KEY,
  event_id      BIGINT            NOT NULL REFERENCES events,
  -- 기사의 발행 시각 — 기사를 뺀 뒤 사건의 첫 · 마지막 기사 시각을 다시 맞추는 입력
  published_at  TIMESTAMPTZ       NOT NULL,
  -- 붙인 후보의 점수, 새 사건이면 가장 높은 후보의 점수(후보가 없으면 null)
  score         DOUBLE PRECISION,
  decided_by    TEXT              NOT NULL CHECK (decided_by IN ('FORMULA', 'EVALUATOR', 'JUDGEMENT_FAILED')),
  criteria_id   BIGINT            NOT NULL REFERENCES event_assignment_criteria,
  assigned_at   TIMESTAMPTZ       NOT NULL
);
CREATE INDEX event_articles_event_idx ON event_articles (event_id);
CREATE INDEX event_articles_criteria_idx ON event_articles (criteria_id);

-- 배정 후보 — 저장 기준 이상인 후보의 점수와 판정 확률(묻지 않았으면 null). 재판정의 재료다.
-- event_id 에는 FK 를 두지 않는다 — 사건을 지울 때 함께 지운다
CREATE TABLE event_article_candidates (
  article_id   BIGINT            NOT NULL,
  event_id     BIGINT            NOT NULL,
  score        DOUBLE PRECISION  NOT NULL,
  probability  DOUBLE PRECISION,
  PRIMARY KEY (article_id, event_id)
);
CREATE INDEX event_article_candidates_event_idx ON event_article_candidates (event_id);

-- 배정 대기 — 표현을 쓴 기사마다 한 행. 배정하면 지운다
CREATE TABLE event_assignment_queue (
  article_id        BIGINT       PRIMARY KEY,
  enqueued_at       TIMESTAMPTZ  NOT NULL,
  attempts          INTEGER      NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  available_at      TIMESTAMPTZ  NOT NULL,
  lease_token       UUID,
  lease_expires_at  TIMESTAMPTZ,
  CHECK ((lease_token IS NULL) = (lease_expires_at IS NULL))
);
CREATE INDEX event_assignment_queue_order_idx ON event_assignment_queue (available_at, article_id);
