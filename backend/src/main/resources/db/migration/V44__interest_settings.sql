-- user 모듈 — 관심 설정(docs/specs/user.md §6 · user-R43 ~ R51).

-- 설정 행 — 사용자마다 하나. tag_set_id 는 선호 버전(태그 선호 전체가 그 버전의 키다 — tag 의 버전 ID, 모듈 밖이라 FK 없음).
-- 관심사는 빈 문자열이 없음이다. 고른 모델은 공급자 · 모델 ID 가 함께 있거나 함께 없다. 요일은 ISO(월 1 ~ 일 7)
CREATE TABLE user_settings (
  user_id            BIGINT       PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
  tag_set_id         BIGINT       NOT NULL CHECK (tag_set_id >= 1),
  timezone           TEXT         NOT NULL,
  digest_time        TIME         NOT NULL,
  interest_text      TEXT         NOT NULL,
  llm_provider       TEXT         CHECK (llm_provider IN ('openai', 'anthropic')),
  llm_model          TEXT,
  weekly_report      BOOLEAN      NOT NULL,
  weekly_report_day  SMALLINT     NOT NULL CHECK (weekly_report_day BETWEEN 1 AND 7),
  monthly_report     BOOLEAN      NOT NULL,
  updated_at         TIMESTAMPTZ  NOT NULL,
  version            BIGINT       NOT NULL,
  CHECK ((llm_provider IS NULL) = (llm_model IS NULL))
);

-- 태그 선호의 현재 상태 — 선호 이력의 투영. 태그 종류마다 모드 하나다(주제는 구독, 지역 · 형식은 제외)
CREATE TABLE user_tag_preferences (
  user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  tag_kind    TEXT         NOT NULL CHECK (tag_kind IN ('TOPIC', 'REGION', 'FORMAT')),
  tag_key     TEXT         NOT NULL,
  mode        TEXT         NOT NULL CHECK (mode IN ('SUBSCRIBE', 'EXCLUDE')),
  created_at  TIMESTAMPTZ  NOT NULL,
  PRIMARY KEY (user_id, tag_kind, tag_key),
  CHECK ((tag_kind = 'TOPIC') = (mode = 'SUBSCRIBE'))
);

-- 선호 이력 — 추가만 한다. tag_set_id 는 기록 당시 버전, cause 는 원인(관심 신호는 USER 행만)
CREATE TABLE user_tag_preference_events (
  id           BIGSERIAL    PRIMARY KEY,
  user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  cause        TEXT         NOT NULL CHECK (cause IN ('USER', 'ONBOARDING', 'TAG_SET_CHANGE')),
  tag_set_id   BIGINT       NOT NULL CHECK (tag_set_id >= 1),
  tag_kind     TEXT         NOT NULL CHECK (tag_kind IN ('TOPIC', 'REGION', 'FORMAT')),
  tag_key      TEXT         NOT NULL,
  mode         TEXT         NOT NULL CHECK (mode IN ('SUBSCRIBE', 'EXCLUDE')),
  action       TEXT         NOT NULL CHECK (action IN ('ADD', 'REMOVE')),
  occurred_at  TIMESTAMPTZ  NOT NULL,
  CHECK ((tag_kind = 'TOPIC') = (mode = 'SUBSCRIBE'))
);
CREATE INDEX user_tag_preference_events_user_idx ON user_tag_preference_events (user_id, occurred_at);

-- 제외 소스 — 소스 단위, 이력 없음. source_id 는 source 의 소스 ID(모듈 밖이라 FK 없음 — SourceDeleted 로 지운다)
CREATE TABLE user_excluded_sources (
  user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  source_id   BIGINT       NOT NULL CHECK (source_id >= 1),
  created_at  TIMESTAMPTZ  NOT NULL,
  PRIMARY KEY (user_id, source_id)
);
-- 소스 삭제가 소스 ID 로 찾는다
CREATE INDEX user_excluded_sources_source_idx ON user_excluded_sources (source_id);
