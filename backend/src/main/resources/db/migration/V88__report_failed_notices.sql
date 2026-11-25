-- user 모듈 — 리포트 실패 알림(docs/specs/user.md §6).
-- 종류 REPORT_FAILED 를 더하고, 같은 사용자 · 리포트 · 실패 시각의 알림은 하나다(같은 이벤트를 거듭 받아도 하나 — 다시 만든 리포트가 또
-- 실패하면 실패 시각이 달라 새 알림이다). 키는 내용의 reportId · failedAt 이다.
ALTER TABLE user_notices DROP CONSTRAINT user_notices_kind_check;
ALTER TABLE user_notices ADD CONSTRAINT user_notices_kind_check
  CHECK (kind IN ('FEED_STATUS', 'PASSWORD_CHANGED', 'KEY_INVALIDATED', 'PREFERENCES_ADJUSTED',
    'STORYLINE_SUBSCRIPTIONS_REMOVED', 'DIGEST_FAILED', 'REPORT_FAILED'));
CREATE UNIQUE INDEX user_notices_report_failed_key
  ON user_notices (user_id, (content ->> 'reportId'), (content ->> 'failedAt'))
  WHERE kind = 'REPORT_FAILED';
