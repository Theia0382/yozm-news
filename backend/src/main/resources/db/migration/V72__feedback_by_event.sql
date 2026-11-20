-- feedback 모듈 — 좋아요 / 싫어요를 다이제스트 항목에 받아 그 항목의 사건마다 기록한다(docs/specs/feedback.md §6 · feedback-R1).
--
-- 기사를 키로 쓰던 현재 상태 · 이력 표를 지우고 사건을 키로 새로 만든다 — 옛 행은 옮기지 않고 버린다(릴리스 전이고 그 데이터를 가진
-- 인스턴스가 없다). user_id · event_id · digest_id 는 다른 모듈의 ID 다 — 모듈 사이라 FK 를 두지 않는다(지운 사건은 feedback-R5 가 따른다)
DROP TABLE feedback_events;
DROP TABLE feedback;

-- 사건마다 현재 상태 — 이력의 투영. (사용자, 사건)마다 많아야 한 행이고 없음이면 행이 없다. 같은 사건의 기록이 차례를 서는 잠금 자리다
-- (feedback-R4). event_id 는 기록한(원래) 사건 ID 다 — 합쳐진 사건은 읽을 때 남는 사건으로 푼다
CREATE TABLE feedback (
  user_id     BIGINT       NOT NULL CHECK (user_id >= 1),
  event_id    BIGINT       NOT NULL CHECK (event_id >= 1),
  value       TEXT         NOT NULL CHECK (value IN ('LIKE', 'DISLIKE')),
  digest_id   BIGINT       NOT NULL CHECK (digest_id >= 1),
  updated_at  TIMESTAMPTZ  NOT NULL,
  PRIMARY KEY (user_id, event_id)
);
-- 지운 사건의 행을 찾는다
CREATE INDEX feedback_event_idx ON feedback (event_id);

-- 이력 — 사건마다 값이 바뀔 때 한 줄. 순서의 정본은 id 다(feedback-R4)
CREATE TABLE feedback_events (
  id           BIGSERIAL    PRIMARY KEY,
  user_id      BIGINT       NOT NULL CHECK (user_id >= 1),
  event_id     BIGINT       NOT NULL CHECK (event_id >= 1),
  value        TEXT         NOT NULL CHECK (value IN ('LIKE', 'DISLIKE', 'NONE')),
  digest_id    BIGINT       NOT NULL CHECK (digest_id >= 1),
  occurred_at  TIMESTAMPTZ  NOT NULL
);
-- 사용자의 기간 이력을 읽는다
CREATE INDEX feedback_events_user_idx ON feedback_events (user_id, occurred_at, id);
-- 사건마다 마지막 이력 행을 읽는다
CREATE INDEX feedback_events_user_event_idx ON feedback_events (user_id, event_id, id);
-- 지운 사건의 행을 찾는다
CREATE INDEX feedback_events_event_idx ON feedback_events (event_id);

-- 항목에 누른 값 — 화면이 보이는 값. 항목 키는 그 항목의 첫 사건(digest_section_events 의 위치 0)이다. 이력이 없다 — 관심 신호는
-- 사건 이력이 맡는다
CREATE TABLE feedback_items (
  user_id        BIGINT       NOT NULL CHECK (user_id >= 1),
  digest_id      BIGINT       NOT NULL CHECK (digest_id >= 1),
  item_event_id  BIGINT       NOT NULL CHECK (item_event_id >= 1),
  value          TEXT         NOT NULL CHECK (value IN ('LIKE', 'DISLIKE')),
  updated_at     TIMESTAMPTZ  NOT NULL,
  PRIMARY KEY (user_id, digest_id, item_event_id)
);
-- 지운 항목 섹션의 행을 찾는다
CREATE INDEX feedback_items_digest_idx ON feedback_items (digest_id);
