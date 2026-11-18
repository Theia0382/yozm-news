-- 구독 제안의 거절 — 사용자 · 기록 당시 태그 체계 버전 · 주제 중분류 키마다 한 행 — docs/specs/user.md §6.
--
-- 관심 신호가 아니라 현재 상태 한 벌만 둔다(이력 없음). tag_set_id 는 기록 당시 버전(FK 없음 — 선호 이력과 같은 칸)이고, 읽을 때
-- 계보로 활성 키로 푼다. 키 문자열은 버전을 넘어 다른 뜻으로 다시 쓰일 수 있어 기본 키에 버전을 넣는다. interest_level 은 거절 때의
-- 관심 x 다 — 기간이 지나도 지금 관심이 이 값보다 커야 다시 제안한다.
CREATE TABLE user_topic_suggestion_declines (
  user_id         BIGINT            NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  tag_set_id      BIGINT            NOT NULL CHECK (tag_set_id >= 1),
  topic_key       TEXT              NOT NULL,
  interest_level  DOUBLE PRECISION  NOT NULL CHECK (interest_level >= -1 AND interest_level <= 1),
  declined_at     TIMESTAMPTZ       NOT NULL,
  PRIMARY KEY (user_id, tag_set_id, topic_key)
);
