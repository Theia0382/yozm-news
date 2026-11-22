-- user 모듈 — 사용자 스토리라인(docs/specs/user.md §6).
--
-- 사용자 스토리라인은 사용자가 관심 신호(좋아요 · 싫어요 · 흐름 추적)를 준 흐름이다. 추적 대상(공용 스토리라인의 포함 나무 꼭대기)과
-- 추적 씨앗(사용자가 누른 스토리라인)을 갖고, 스토리라인 구독 행이 이것을 가리킨다. storyline_id 는 storyline 의 스토리라인 ID 다 —
-- 모듈 밖이라 FK 가 없고, 합쳐지거나 지워진 것은 따라가기가 고친다.

CREATE TABLE user_storylines (
  id          BIGSERIAL    PRIMARY KEY,
  user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  created_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX user_storylines_user_idx ON user_storylines (user_id);

-- 추적 대상 — 마지막으로 맞춘 공용 꼭대기
CREATE TABLE user_storyline_members (
  user_storyline_id  BIGINT  NOT NULL REFERENCES user_storylines (id) ON DELETE CASCADE,
  storyline_id       BIGINT  NOT NULL CHECK (storyline_id >= 1),
  PRIMARY KEY (user_storyline_id, storyline_id)
);
-- 합치거나 지운 스토리라인을 추적하는 사용자를 찾는다
CREATE INDEX user_storyline_members_storyline_idx ON user_storyline_members (storyline_id);

-- 추적 씨앗 — 사용자가 누른(또는 갈라질 때 물려받은) 스토리라인. 꼭대기가 아닐 수 있다. 추적 중인 사용자 스토리라인에만 있다
CREATE TABLE user_storyline_tracked_seeds (
  user_storyline_id  BIGINT  NOT NULL REFERENCES user_storylines (id) ON DELETE CASCADE,
  storyline_id       BIGINT  NOT NULL CHECK (storyline_id >= 1),
  PRIMARY KEY (user_storyline_id, storyline_id)
);
CREATE INDEX user_storyline_tracked_seeds_storyline_idx ON user_storyline_tracked_seeds (storyline_id);

-- 이력의 원인에 갈라짐을 더한다 — 추적 중인 사용자 스토리라인이 갈라져 조각이 추적을 물려받았다
ALTER TABLE user_storyline_subscription_events DROP CONSTRAINT user_storyline_subscription_events_cause_check;
ALTER TABLE user_storyline_subscription_events ADD CONSTRAINT user_storyline_subscription_events_cause_check
  CHECK (cause IN ('USER', 'STORYLINE_MERGED', 'STORYLINE_REMOVED', 'STORYLINE_SPLIT'));

-- 구독 행이 사용자 스토리라인을 가리킨다. 기존 구독마다 사용자 스토리라인 하나 · 추적 대상 하나 · 추적 씨앗 하나를 만든다 —
-- 같은 흐름에 든 구독(상위와 하위, 이어진 앞과 뒤)은 다음 따라가기가 합친다. 구독 시각은 그대로다
ALTER TABLE user_storyline_subscriptions ADD COLUMN user_storyline_id BIGINT REFERENCES user_storylines (id) ON DELETE CASCADE;

DO $$
DECLARE
  subscription RECORD;
  created_id   BIGINT;
BEGIN
  FOR subscription IN
    SELECT user_id, storyline_id, created_at FROM user_storyline_subscriptions ORDER BY user_id, storyline_id
  LOOP
    INSERT INTO user_storylines (user_id, created_at)
    VALUES (subscription.user_id, subscription.created_at)
    RETURNING id INTO created_id;
    INSERT INTO user_storyline_members (user_storyline_id, storyline_id)
    VALUES (created_id, subscription.storyline_id);
    INSERT INTO user_storyline_tracked_seeds (user_storyline_id, storyline_id)
    VALUES (created_id, subscription.storyline_id);
    UPDATE user_storyline_subscriptions
       SET user_storyline_id = created_id
     WHERE user_id = subscription.user_id AND storyline_id = subscription.storyline_id;
  END LOOP;
END $$;

ALTER TABLE user_storyline_subscriptions ALTER COLUMN user_storyline_id SET NOT NULL;
ALTER TABLE user_storyline_subscriptions DROP CONSTRAINT user_storyline_subscriptions_pkey;
DROP INDEX user_storyline_subscriptions_storyline_idx;
ALTER TABLE user_storyline_subscriptions DROP COLUMN storyline_id;
ALTER TABLE user_storyline_subscriptions ADD PRIMARY KEY (user_storyline_id);
CREATE INDEX user_storyline_subscriptions_user_idx ON user_storyline_subscriptions (user_id);
