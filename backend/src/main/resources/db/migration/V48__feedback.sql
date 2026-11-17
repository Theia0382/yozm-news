-- feedback 모듈 — 좋아요 / 싫어요(docs/specs/feedback.md §6 · feedback-R1).
-- user_id · article_id · digest_id 는 다른 모듈의 ID 다 — 모듈 사이라 FK 를 두지 않는다(지운 기사는 feedback-R5 가 따른다)

-- 현재 상태 — (사용자, 기사)마다 많아야 한 행. 없음이면 행이 없다
CREATE TABLE feedback (
  user_id     BIGINT       NOT NULL CHECK (user_id >= 1),
  article_id  BIGINT       NOT NULL CHECK (article_id >= 1),
  value       TEXT         NOT NULL CHECK (value IN ('LIKE', 'DISLIKE')),
  digest_id   BIGINT       NOT NULL CHECK (digest_id >= 1),
  updated_at  TIMESTAMPTZ  NOT NULL,
  PRIMARY KEY (user_id, article_id)
);
-- 지운 기사의 행을 찾는다
CREATE INDEX feedback_article_idx ON feedback (article_id);

-- 이력 — 값이 바뀔 때마다 한 줄. 순서의 정본은 id 다(feedback-R4)
CREATE TABLE feedback_events (
  id           BIGSERIAL    PRIMARY KEY,
  user_id      BIGINT       NOT NULL CHECK (user_id >= 1),
  article_id   BIGINT       NOT NULL CHECK (article_id >= 1),
  value        TEXT         NOT NULL CHECK (value IN ('LIKE', 'DISLIKE', 'NONE')),
  digest_id    BIGINT       NOT NULL CHECK (digest_id >= 1),
  occurred_at  TIMESTAMPTZ  NOT NULL
);
-- 사용자의 기간 이력을 읽는다
CREATE INDEX feedback_events_user_idx ON feedback_events (user_id, occurred_at, id);
-- 지운 기사의 행을 찾는다
CREATE INDEX feedback_events_article_idx ON feedback_events (article_id);
