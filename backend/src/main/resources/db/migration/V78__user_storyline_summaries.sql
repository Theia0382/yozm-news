-- user 모듈 — 사용자 스토리라인의 정리와 시도 기록(docs/specs/user.md §6).
-- 정리는 그 사용자의 LLM 키로 만든 글이다 — 흐름 전체, 꼭대기마다, 정리에 넣은 사건.
-- storyline_id · event_id · article_id 는 다른 모듈의 ID 다 — 모듈 사이라 FK 를 두지 않는다.

-- 흐름 전체의 정리 — 사용자 스토리라인마다 많아야 한 행. 사용자 스토리라인을 지우면 함께 지워진다.
CREATE TABLE user_storyline_summaries (
  user_storyline_id  BIGINT       PRIMARY KEY REFERENCES user_storylines (id) ON DELETE CASCADE,
  title              TEXT         NOT NULL,
  summary            TEXT         NOT NULL,
  recent             TEXT         NOT NULL,
  -- 마지막 정리의 모델과 토큰
  provider           TEXT         NOT NULL,
  model              TEXT         NOT NULL,
  input_tokens       INTEGER      NOT NULL CHECK (input_tokens >= 0),
  output_tokens      INTEGER      NOT NULL CHECK (output_tokens >= 0),
  -- 처음 정리한 시각과 마지막으로 정리한 시각
  created_at         TIMESTAMPTZ  NOT NULL,
  updated_at         TIMESTAMPTZ  NOT NULL,
  -- 저장 때 견주는 판 — 0 부터, 고쳐 쓸 때마다 하나 오른다
  version            INTEGER      NOT NULL CHECK (version >= 0)
);

-- 꼭대기마다의 정리 — 이 행들의 스토리라인이 정리 때의 추적 대상이다. 꼭대기가 하나면 글이 흐름 전체와 같다.
CREATE TABLE user_storyline_top_summaries (
  user_storyline_id  BIGINT  NOT NULL REFERENCES user_storyline_summaries (user_storyline_id) ON DELETE CASCADE,
  storyline_id       BIGINT  NOT NULL CHECK (storyline_id >= 1),
  title              TEXT    NOT NULL,
  summary            TEXT    NOT NULL,
  recent             TEXT    NOT NULL,
  PRIMARY KEY (user_storyline_id, storyline_id)
);

-- 정리에 넣은 사건과 그때의 대표 기사 — 새 사건을 가르고, 지운 기사를 넣은 정리를 찾는다.
CREATE TABLE user_storyline_summary_events (
  user_storyline_id  BIGINT  NOT NULL REFERENCES user_storyline_summaries (user_storyline_id) ON DELETE CASCADE,
  event_id           BIGINT  NOT NULL CHECK (event_id >= 1),
  article_id         BIGINT  NOT NULL CHECK (article_id >= 1),
  PRIMARY KEY (user_storyline_id, event_id)
);
CREATE INDEX user_storyline_summary_events_article_idx ON user_storyline_summary_events (article_id);

-- 시도 기록 — 공급자에 보낸 호출의 수와 토큰 합, 연속 실패. 정리가 아직 없거나 지워진 사용자 스토리라인에도 있다.
CREATE TABLE user_storyline_summary_attempts (
  user_storyline_id    BIGINT       PRIMARY KEY REFERENCES user_storylines (id) ON DELETE CASCADE,
  calls                INTEGER      NOT NULL CHECK (calls >= 1),
  total_input_tokens   BIGINT       NOT NULL CHECK (total_input_tokens >= 0),
  total_output_tokens  BIGINT       NOT NULL CHECK (total_output_tokens >= 0),
  -- 연속 실패 수와 마지막 실패 — 저장에 성공하면 0 이고 코드 · 시각이 없다
  failures             INTEGER      NOT NULL CHECK (failures >= 0),
  failure_code         TEXT,
  failed_at            TIMESTAMPTZ,
  CHECK ((failures = 0) = (failure_code IS NULL)),
  CHECK ((failures = 0) = (failed_at IS NULL))
);
