-- event 모듈 — 사건 묶기 대기와 기사 표현(사건 배정 입력).
--
-- article_id 는 article 모듈의 articles 를 가리키지만 FK 를 두지 않는다 — 모듈 사이는 ID 로만 참조한다.

-- 사건 묶기 대기 — 기사마다 한 행. 표현을 쓰면 지운다
CREATE TABLE event_intake_queue (
  article_id        BIGINT       PRIMARY KEY,
  enqueued_at       TIMESTAMPTZ  NOT NULL,
  lease_token       UUID,
  lease_expires_at  TIMESTAMPTZ,
  CHECK ((lease_token IS NULL) = (lease_expires_at IS NULL))
);
CREATE INDEX event_intake_queue_order_idx ON event_intake_queue (enqueued_at, article_id);

-- 기사 표현 — 기사 × 임베딩 모델 버전마다 한 행. 차원은 모델마다 달라 컬럼에 정하지 않는다 — 길이는 쓰기 전에 앱이 확인한다
CREATE TABLE article_match_features (
  article_id     BIGINT       NOT NULL,
  model_version  TEXT         NOT NULL,
  embedding      vector       NOT NULL,
  -- 핵심 말 — 처음 나온 순서, 같은 말은 한 번
  entities       TEXT[]       NOT NULL,
  created_at     TIMESTAMPTZ  NOT NULL,
  PRIMARY KEY (article_id, model_version)
);
