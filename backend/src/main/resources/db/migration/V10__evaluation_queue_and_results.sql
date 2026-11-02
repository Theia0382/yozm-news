-- evaluation 모듈 — 평가 대기와 평가 결과.
--
-- article_id 는 article 모듈의 articles 를 가리키지만 FK 를 두지 않는다 — 모듈 사이는 ID 로만 참조한다. tag_set_id 는 같은 모듈의
-- 태그 체계 버전이라 FK 를 둔다. 결과의 태그 · 형식 키가 그 버전 체계와 맞는지는 저장 전에 domain 이 본다.

-- 평가 대기 — 기사마다 한 행. 성공하면 지우고, 실패로 남은 행은 failure 를 갖는다
CREATE TABLE evaluation_queue (
  article_id        BIGINT       PRIMARY KEY,
  enqueued_at       TIMESTAMPTZ  NOT NULL,
  -- 이 시각부터 평가할 수 있다 — 정착 시간 · 재시도 간격이 정한다
  eligible_at       TIMESTAMPTZ  NOT NULL,
  -- 지금까지의 일시 실패 수
  attempts          INTEGER      NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  lease_token       UUID,
  lease_expires_at  TIMESTAMPTZ,
  failure           TEXT         CHECK (failure IN ('ARTICLE_REJECTED', 'RETRIES_EXHAUSTED')),
  -- 마지막 실패의 운영자용 문구
  last_error        TEXT,
  CHECK ((lease_token IS NULL) = (lease_expires_at IS NULL)),
  CHECK (failure IS NULL OR lease_token IS NULL)
);
CREATE INDEX evaluation_queue_due_idx ON evaluation_queue (eligible_at, article_id) WHERE failure IS NULL;

-- 평가 — 기사 · 태그 체계 버전마다 한 번
CREATE TABLE article_evaluations (
  id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  article_id               BIGINT           NOT NULL,
  tag_set_id               BIGINT           NOT NULL REFERENCES tag_sets (id),
  evaluator                TEXT             NOT NULL,
  model_version            TEXT             NOT NULL,
  -- 대표 형식 — 확률이 가장 큰 선택지와 그 확률. 분포 전체는 format_distribution
  format_key               TEXT             NOT NULL,
  format_probability       DOUBLE PRECISION NOT NULL CHECK (format_probability BETWEEN 0 AND 1),
  format_distribution      JSONB            NOT NULL,
  -- 중요도 기대값 — 확률 합이 0 이면 없다
  importance               DOUBLE PRECISION CHECK (importance BETWEEN 1 AND 5),
  importance_distribution  JSONB            NOT NULL,
  raw_response             JSONB            NOT NULL,
  input_tokens             INTEGER          NOT NULL CHECK (input_tokens >= 0),
  evaluated_at             TIMESTAMPTZ      NOT NULL,
  UNIQUE (article_id, tag_set_id)
);

-- 주제 · 지역 태그 — 평가 때의 저장 기준 이상인 확률. 붙었는지는 조회 때 태그 기준으로 판정한다
CREATE TABLE article_tags (
  evaluation_id  BIGINT           NOT NULL REFERENCES article_evaluations (id),
  article_id     BIGINT           NOT NULL,
  tag_kind       TEXT             NOT NULL CHECK (tag_kind IN ('TOPIC', 'REGION')),
  tag_key        TEXT             NOT NULL,
  probability    DOUBLE PRECISION NOT NULL CHECK (probability BETWEEN 0 AND 1),
  PRIMARY KEY (evaluation_id, tag_kind, tag_key)
);
CREATE INDEX article_tags_article_idx ON article_tags (article_id);

-- 이 모듈이 생기기 전에 저장된 기사를 대기에 넣는다. 한 번의 스키마 이행이라 article 모듈의 articles 를 읽는다. 이미 저장된 기사라
-- 정착 시간을 두지 않는다
INSERT INTO evaluation_queue (article_id, enqueued_at, eligible_at) SELECT id, now(), now() FROM articles;
