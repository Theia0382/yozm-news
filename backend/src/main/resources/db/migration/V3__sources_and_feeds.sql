-- 소스 · 피드와 피드의 수집 상태, 소스의 요청 시작 예약.
--
-- 운영자가 쓰는 운영 설정(sources · feeds)과 수집 작업만 쓰는 수집 상태(feed_poll_states)를 다른 테이블에 둔다 —
-- 운영자 변경과 수집 결과가 서로를 덮어쓰지 않는다. 수집 주기의 하한은 source 모듈의 PollInterval 이 정본이라
-- 여기에 CHECK 로 다시 두지 않는다.

CREATE TABLE sources (
  id                    BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  -- 프리셋을 참조하면 프리셋 키(프리셋 파일 이름), 아니면 운영자 프로필 원문 — 둘 중 하나다
  preset_key            TEXT        UNIQUE,
  profile               TEXT,
  enabled               BOOLEAN     NOT NULL,
  -- 운영자 소스 주기 설정(분). 정하지 않았으면 비어 있다
  poll_interval_minutes INTEGER,
  version               BIGINT      NOT NULL,
  created_at            TIMESTAMPTZ NOT NULL,
  CHECK ((preset_key IS NULL) <> (profile IS NULL))
);

CREATE TABLE feeds (
  id                    BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  source_id             BIGINT      NOT NULL REFERENCES sources (id),
  -- 수집 프로필 안의 피드 식별자. URL · 섹션은 프로필에 있다
  feed_key              TEXT        NOT NULL,
  enabled               BOOLEAN     NOT NULL,
  -- 운영자 피드 주기 설정(분). 정하지 않았으면 비어 있다
  poll_interval_minutes INTEGER,
  version               BIGINT      NOT NULL,
  created_at            TIMESTAMPTZ NOT NULL,
  UNIQUE (source_id, feed_key)
);

CREATE TABLE feed_poll_states (
  feed_id               BIGINT      PRIMARY KEY REFERENCES feeds (id),
  -- 다음에 수집 대상이 되는 시각. 점유 중이면 예약 시각 + 점유 기간이다
  next_poll_at          TIMESTAMPTZ NOT NULL,
  -- 점유 — 토큰은 그 회차 수집 작업의 JobRunr 작업 id
  lease_token           UUID,
  lease_scheduled_at    TIMESTAMPTZ,
  -- 조건부 요청 값 — 응답 헤더 원문
  etag                  TEXT,
  last_modified         TEXT,
  last_polled_at        TIMESTAMPTZ,
  last_success_at       TIMESTAMPTZ,
  last_new_item_at      TIMESTAMPTZ,
  consecutive_failures  INTEGER     NOT NULL,
  -- 마지막 실패 — 성공한 뒤에도 남는다
  last_error_reason     TEXT,
  last_error_message    TEXT,
  last_error_status     INTEGER,
  last_error_at         TIMESTAMPTZ,
  -- 마지막 결과가 거친 영구 이동(301 · 308)의 대상
  moved_to              TEXT,
  last_overflow_at      TIMESTAMPTZ,
  overflow_count        INTEGER     NOT NULL,
  -- 직전 성공 수집의 항목 키. 비어 있으면(NULL) 아직 성공한 수집이 없다 — 빈 배열과 다르다
  previous_item_keys    TEXT[],
  CHECK ((lease_token IS NULL) = (lease_scheduled_at IS NULL)),
  CHECK ((last_error_reason IS NULL) = (last_error_at IS NULL))
);

CREATE INDEX feed_poll_states_next_poll_at_idx ON feed_poll_states (next_poll_at);

-- 소스마다 다음 요청을 시작할 수 있는 시각 — 같은 언론사의 요청 시작 사이를 띄우는 예약이다. 수집 작업이 요청 직전에 이 행을
-- 잠그고 시작 시각을 받는다. 비어 있으면(NULL) 아직 요청한 적이 없다
CREATE TABLE source_fetch_slots (
  source_id             BIGINT      PRIMARY KEY REFERENCES sources (id),
  next_fetch_at         TIMESTAMPTZ
);
