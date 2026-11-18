-- user 모듈 — 다이제스트 실패 알림(docs/specs/user.md §6).

-- 다이제스트가 실패로 끝났다는 알림 — 실패한 다이제스트마다 하나(모으지 않는다)
ALTER TABLE user_notices DROP CONSTRAINT user_notices_kind_check;
ALTER TABLE user_notices ADD CONSTRAINT user_notices_kind_check
  CHECK (kind IN ('FEED_STATUS', 'PASSWORD_CHANGED', 'KEY_INVALIDATED', 'PREFERENCES_ADJUSTED',
    'STORYLINE_SUBSCRIPTIONS_REMOVED', 'DIGEST_FAILED'));
-- 같은 다이제스트의 알림은 하나다 — 같은 이벤트를 거듭 받아도 하나만 남는다
CREATE UNIQUE INDEX user_notices_digest_failed_key
  ON user_notices (user_id, (content ->> 'digestId'))
  WHERE kind = 'DIGEST_FAILED';
