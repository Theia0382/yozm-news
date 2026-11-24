-- user 모듈 — 관심 · 알림 표는 계정을 ID 로만 가리킨다(docs/specs/user.md §6 · ADR-0308).
--
-- 계정 표(users)는 account 모듈의 것이다. 모듈 사이에는 FK 를 두지 않으므로 user 의 표 10개가 users 에 건 FK 를 지우고, 다른
-- 모듈의 user_id 와 같이 1 이상 검사를 둔다. 계정을 지우는 길은 없다 — 생기면 다른 모듈처럼 계정 쪽이 알리는 사실로 따른다.
-- user 안의 FK(user_storyline_subscriptions → user_storylines 등)와 account 안의 FK(자격 · 복구 코드 · 초대)는 그대로다.

ALTER TABLE user_notices DROP CONSTRAINT user_notices_user_id_fkey;
ALTER TABLE user_notices ADD CONSTRAINT user_notices_user_id_check CHECK (user_id >= 1);

ALTER TABLE user_feed_status_receipts DROP CONSTRAINT user_feed_status_receipts_user_id_fkey;
ALTER TABLE user_feed_status_receipts ADD CONSTRAINT user_feed_status_receipts_user_id_check CHECK (user_id >= 1);

ALTER TABLE user_settings DROP CONSTRAINT user_settings_user_id_fkey;
ALTER TABLE user_settings ADD CONSTRAINT user_settings_user_id_check CHECK (user_id >= 1);

ALTER TABLE user_tag_preferences DROP CONSTRAINT user_tag_preferences_user_id_fkey;
ALTER TABLE user_tag_preferences ADD CONSTRAINT user_tag_preferences_user_id_check CHECK (user_id >= 1);

ALTER TABLE user_tag_preference_events DROP CONSTRAINT user_tag_preference_events_user_id_fkey;
ALTER TABLE user_tag_preference_events ADD CONSTRAINT user_tag_preference_events_user_id_check CHECK (user_id >= 1);

ALTER TABLE user_excluded_sources DROP CONSTRAINT user_excluded_sources_user_id_fkey;
ALTER TABLE user_excluded_sources ADD CONSTRAINT user_excluded_sources_user_id_check CHECK (user_id >= 1);

ALTER TABLE user_storyline_subscriptions DROP CONSTRAINT user_storyline_subscriptions_user_id_fkey;
ALTER TABLE user_storyline_subscriptions ADD CONSTRAINT user_storyline_subscriptions_user_id_check CHECK (user_id >= 1);

ALTER TABLE user_storyline_subscription_events DROP CONSTRAINT user_storyline_subscription_events_user_id_fkey;
ALTER TABLE user_storyline_subscription_events
  ADD CONSTRAINT user_storyline_subscription_events_user_id_check CHECK (user_id >= 1);

ALTER TABLE user_topic_suggestion_declines DROP CONSTRAINT user_topic_suggestion_declines_user_id_fkey;
ALTER TABLE user_topic_suggestion_declines ADD CONSTRAINT user_topic_suggestion_declines_user_id_check CHECK (user_id >= 1);

ALTER TABLE user_storylines DROP CONSTRAINT user_storylines_user_id_fkey;
ALTER TABLE user_storylines ADD CONSTRAINT user_storylines_user_id_check CHECK (user_id >= 1);
