-- user 모듈 — 키 무효 알림(docs/specs/user.md §6).

-- LLM 키가 무효가 됐다는 알림 — 무효가 되는 전이마다 하나(모으지 않는다)
ALTER TABLE user_notices DROP CONSTRAINT user_notices_kind_check;
ALTER TABLE user_notices ADD CONSTRAINT user_notices_kind_check
  CHECK (kind IN ('FEED_STATUS', 'PASSWORD_CHANGED', 'KEY_INVALIDATED'));
-- 같은 전이(사용자 · 키 ID · 무효 시각)의 알림은 하나다 — 같은 이벤트를 거듭 받아도 하나만 남는다
CREATE UNIQUE INDEX user_notices_key_invalidated_key
  ON user_notices (user_id, (content ->> 'keyId'), (content ->> 'invalidatedAt'))
  WHERE kind = 'KEY_INVALIDATED';
