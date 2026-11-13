-- user 모듈 — 화면 내 알림과 피드 상태 알림의 반영 기준(docs/specs/user.md §6).

-- 화면 내 알림 — 받는 사용자마다 한 행. 종류는 닫힌 집합이고 내용은 종류마다 정한 jsonb 다. 판(revision)은 내용을 고칠 때마다
-- 하나 오르고, 읽음은 화면이 받은 판이 그대로일 때만 기록된다
CREATE TABLE user_notices (
  id          BIGSERIAL    PRIMARY KEY,
  user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  kind        TEXT         NOT NULL CHECK (kind IN ('FEED_STATUS')),
  content     JSONB        NOT NULL,
  revision    INTEGER      NOT NULL CHECK (revision >= 1),
  created_at  TIMESTAMPTZ  NOT NULL,
  updated_at  TIMESTAMPTZ  NOT NULL,
  read_at     TIMESTAMPTZ
);
-- 알림함 목록 — 사용자의 최근 알림부터
CREATE INDEX user_notices_user_id_id_idx ON user_notices (user_id, id DESC);
-- 피드 상태 알림은 사용자마다 안 읽은 것이 많아야 하나다 — 전이를 그 알림에 모은다
CREATE UNIQUE INDEX user_notices_unread_feed_status_key ON user_notices (user_id)
  WHERE kind = 'FEED_STATUS' AND read_at IS NULL;
-- 읽은 알림 정리가 읽은 시각으로 찾는다
CREATE INDEX user_notices_read_at_idx ON user_notices (read_at) WHERE read_at IS NOT NULL;

-- 피드 상태 알림의 반영 기준 — 사용자마다 반영한 마지막 검사 회차의 판정 시각. 읽은 알림과 무관하게 남아, 다시 처리된 회차와 늦게
-- 온 앞선 회차를 거른다. 판정 시각이 없는 행은 리스너가 잠그려고 먼저 넣는 자리 행이다
CREATE TABLE user_feed_status_receipts (
  user_id           BIGINT       PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
  received_through  TIMESTAMPTZ
);
