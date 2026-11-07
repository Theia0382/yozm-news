-- event 모듈 — 사건 조회의 기간 인덱스, 큰 사건 기준과 그 이력.

-- 기간 조회 — 기간 안 배정을 발행 시각으로 고른다
CREATE INDEX event_articles_published_idx ON event_articles (published_at);

-- 큰 사건 기준 — 지금 값 한 행. 처음 값은 기동 때 앱이 넣는다(정본은 코드의 기본값)
CREATE TABLE event_big_criteria (
  id              SMALLINT       PRIMARY KEY CHECK (id = 1),
  min_sources     INTEGER        NOT NULL CHECK (min_sources >= 1),
  min_importance  NUMERIC(3, 2)  NOT NULL CHECK (min_importance BETWEEN 1 AND 5),
  changed_at      TIMESTAMPTZ    NOT NULL
);

-- 큰 사건 기준 이력 — 추가만 한다. 처음 값은 이전 값이 없다
CREATE TABLE event_big_criteria_changes (
  id                       BIGSERIAL      PRIMARY KEY,
  previous_min_sources     INTEGER,
  previous_min_importance  NUMERIC(3, 2),
  min_sources              INTEGER        NOT NULL,
  min_importance           NUMERIC(3, 2)  NOT NULL,
  actor                    TEXT           NOT NULL CHECK (actor IN ('SYSTEM', 'OPERATOR')),
  changed_at               TIMESTAMPTZ    NOT NULL,
  CHECK ((previous_min_sources IS NULL) = (previous_min_importance IS NULL))
);
