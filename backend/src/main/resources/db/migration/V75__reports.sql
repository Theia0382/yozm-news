-- report 모듈 — 변화 리포트(docs/specs/report.md §6).
-- user_id · storyline_id · event_id 는 다른 모듈의 ID 다 — 모듈 사이라 FK 를 두지 않는다. 합쳐지거나 지워진 사건 · 스토리라인은 조회가 푼다.
-- 문자열 칸은 TEXT 다 — 길이 상한은 코드(ReportOutputSchema · Report)가 갖는다
CREATE TABLE reports (
  id             BIGSERIAL    PRIMARY KEY,
  user_id        BIGINT       NOT NULL CHECK (user_id >= 1),
  period_kind    TEXT         NOT NULL CHECK (period_kind IN ('WEEK','MONTH')),
  period_start   TIMESTAMPTZ  NOT NULL,
  period_end     TIMESTAMPTZ  NOT NULL,
  status         TEXT         NOT NULL CHECK (status IN ('SCHEDULED','RUNNING','READY','EMPTY','FAILED')),
  provider       TEXT         CHECK (provider IN ('openai','anthropic','openrouter')),
  model          TEXT,
  input_tokens   INTEGER      CHECK (input_tokens >= 0),
  output_tokens  INTEGER      CHECK (output_tokens >= 0),
  duration_ms    BIGINT       CHECK (duration_ms >= 0),
  stats_snapshot JSONB,
  error_code     TEXT,
  error_message  TEXT,
  created_at     TIMESTAMPTZ  NOT NULL,
  started_at     TIMESTAMPTZ,
  completed_at   TIMESTAMPTZ,
  version        BIGINT       NOT NULL,
  CHECK (period_end > period_start),
  CHECK ((provider IS NULL) = (model IS NULL)),
  CONSTRAINT reports_started CHECK ((status = 'SCHEDULED') = (started_at IS NULL) AND (started_at IS NULL) = (provider IS NULL)),
  CONSTRAINT reports_snapshot CHECK (started_at IS NULL OR stats_snapshot IS NOT NULL),
  CHECK ((status IN ('READY','EMPTY','FAILED')) = (completed_at IS NOT NULL)),
  CHECK ((status = 'FAILED') = (error_code IS NOT NULL)),
  CHECK ((input_tokens IS NULL) = (output_tokens IS NULL))
);
CREATE INDEX reports_user_id_idx ON reports (user_id, id DESC);

CREATE TABLE report_sections (
  id         BIGSERIAL    PRIMARY KEY,
  report_id  BIGINT       NOT NULL REFERENCES reports (id) ON DELETE CASCADE,
  kind       TEXT         NOT NULL CHECK (kind IN ('OVERVIEW','STORYLINES','COVERAGE','INTERESTS','RISE_AND_FALL')),
  position   INTEGER      NOT NULL CHECK (position >= 0),
  title      TEXT,
  body       TEXT         NOT NULL,
  CONSTRAINT report_sections_position_key UNIQUE (report_id, position),
  CONSTRAINT report_sections_kind_key UNIQUE (report_id, kind)
);

CREATE TABLE report_items (
  report_section_id BIGINT   NOT NULL REFERENCES report_sections (id) ON DELETE CASCADE,
  position          INTEGER  NOT NULL CHECK (position >= 0),
  storyline_id      BIGINT   CHECK (storyline_id >= 1),
  event_id          BIGINT   CHECK (event_id >= 1),
  PRIMARY KEY (report_section_id, position),
  CONSTRAINT report_items_one_target CHECK ((storyline_id IS NULL) <> (event_id IS NULL))
);
