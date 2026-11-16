-- digest 모듈 — 다이제스트(docs/specs/digest.md §6).
-- user_id · article_id 는 다른 모듈의 ID 다 — 모듈 사이라 FK 를 두지 않는다. 문자열 칸은 TEXT 다 — 길이 상한은 코드(DigestOutputSchema · Digest)가 갖는다
CREATE TABLE digests (
  id              BIGSERIAL    PRIMARY KEY,
  user_id         BIGINT       NOT NULL CHECK (user_id >= 1),
  digest_date     DATE         NOT NULL,
  period_start    TIMESTAMPTZ  NOT NULL,
  period_end      TIMESTAMPTZ  NOT NULL,
  status          TEXT         NOT NULL CHECK (status IN ('SCHEDULED','RUNNING','READY','EMPTY','FAILED')),
  trigger         TEXT         NOT NULL CHECK (trigger IN ('SCHEDULED','MANUAL')),
  provider        TEXT         CHECK (provider IN ('openai','anthropic')),
  model           TEXT,
  candidate_count INTEGER      CHECK (candidate_count >= 0),
  input_tokens    INTEGER      CHECK (input_tokens >= 0),
  output_tokens   INTEGER      CHECK (output_tokens >= 0),
  duration_ms     BIGINT       CHECK (duration_ms >= 0),
  error_code      TEXT,
  error_message   TEXT,
  created_at      TIMESTAMPTZ  NOT NULL,
  started_at      TIMESTAMPTZ,
  completed_at    TIMESTAMPTZ,
  version         BIGINT       NOT NULL,
  CHECK (period_end > period_start),
  CHECK ((provider IS NULL) = (model IS NULL)),
  CHECK ((status IN ('READY','EMPTY','FAILED')) = (completed_at IS NOT NULL)),
  CHECK ((status = 'FAILED') = (error_code IS NOT NULL))
);
CREATE INDEX digests_user_date_idx ON digests (user_id, digest_date);

CREATE TABLE digest_sections (
  id         BIGSERIAL    PRIMARY KEY,
  digest_id  BIGINT       NOT NULL REFERENCES digests (id) ON DELETE CASCADE,
  kind       TEXT         NOT NULL CHECK (kind IN ('OVERVIEW','TOP_STORY','MORE_HEADLINES')),
  position   INTEGER      NOT NULL CHECK (position >= 0),
  title      TEXT,
  body       TEXT,
  CONSTRAINT digest_sections_position_key UNIQUE (digest_id, position)
);

CREATE TABLE digest_items (
  digest_section_id BIGINT      NOT NULL REFERENCES digest_sections (id) ON DELETE CASCADE,
  position          INTEGER     NOT NULL CHECK (position >= 0),
  article_id        BIGINT      NOT NULL CHECK (article_id >= 1),
  note              TEXT,
  selection_reason  TEXT        NOT NULL CHECK (selection_reason IN ('SUBSCRIBED','INTEREST','MAJOR_STORYLINE')),
  PRIMARY KEY (digest_section_id, position)
);
CREATE INDEX digest_items_article_idx ON digest_items (article_id);

CREATE TABLE digest_candidates (
  digest_id  BIGINT      NOT NULL REFERENCES digests (id) ON DELETE CASCADE,
  alias      TEXT        NOT NULL,
  article_id BIGINT      NOT NULL CHECK (article_id >= 1),
  PRIMARY KEY (digest_id, alias),
  CONSTRAINT digest_candidates_article_key UNIQUE (digest_id, article_id)
);
CREATE INDEX digest_candidates_article_idx ON digest_candidates (article_id);
