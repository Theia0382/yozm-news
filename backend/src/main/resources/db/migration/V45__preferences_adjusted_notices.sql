-- user 모듈 — 선호 조정 알림(docs/specs/user.md §6 · user-R48).

-- 태그 체계의 활성 전환이 사용자의 선호를 해제하거나 더했다는 알림 — 사용자 · 활성 전환마다 하나(모으지 않는다)
ALTER TABLE user_notices DROP CONSTRAINT user_notices_kind_check;
ALTER TABLE user_notices ADD CONSTRAINT user_notices_kind_check
  CHECK (kind IN ('FEED_STATUS', 'PASSWORD_CHANGED', 'KEY_INVALIDATED', 'PREFERENCES_ADJUSTED'));
