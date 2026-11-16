-- user 모듈 — 스토리라인 구독(docs/specs/user.md §6 · user-R52 ~ R54).

-- 스토리라인 구독의 현재 상태 — 이력의 투영. storyline_id 는 storyline 의 스토리라인 ID(모듈 밖이라 FK 없음 — StorylinesRetired 로 따른다)
CREATE TABLE user_storyline_subscriptions (
  user_id       BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  storyline_id  BIGINT       NOT NULL CHECK (storyline_id >= 1),
  created_at    TIMESTAMPTZ  NOT NULL,
  PRIMARY KEY (user_id, storyline_id)
);
-- 합치거나 지운 스토리라인의 구독자를 찾는다
CREATE INDEX user_storyline_subscriptions_storyline_idx ON user_storyline_subscriptions (storyline_id);

-- 스토리라인 구독 이력 — 추가만 한다. 관심 신호는 cause = USER 행만
CREATE TABLE user_storyline_subscription_events (
  id            BIGSERIAL    PRIMARY KEY,
  user_id       BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  storyline_id  BIGINT       NOT NULL CHECK (storyline_id >= 1),
  action        TEXT         NOT NULL CHECK (action IN ('ADD', 'REMOVE')),
  cause         TEXT         NOT NULL CHECK (cause IN ('USER', 'STORYLINE_MERGED', 'STORYLINE_REMOVED')),
  occurred_at   TIMESTAMPTZ  NOT NULL
);
-- 사용자의 기간 이력을 시각 · 행 순으로 읽는다
CREATE INDEX user_storyline_subscription_events_user_idx ON user_storyline_subscription_events (user_id, occurred_at, id);

-- 사라진 구독 알림 — 구독한 스토리라인이 지워졌다(따라가기 한 번마다 하나)
ALTER TABLE user_notices DROP CONSTRAINT user_notices_kind_check;
ALTER TABLE user_notices ADD CONSTRAINT user_notices_kind_check
  CHECK (kind IN ('FEED_STATUS', 'PASSWORD_CHANGED', 'KEY_INVALIDATED', 'PREFERENCES_ADJUSTED',
    'STORYLINE_SUBSCRIPTIONS_REMOVED'));
