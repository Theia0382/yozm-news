-- user 모듈 — 가입 정책(운영자 설정)과 초대(docs/specs/user.md §6).

-- 가입 정책 — 지금 값 한 행. 처음 값(닫힘)은 기동 때 앱이 넣는다(정본은 코드)
CREATE TABLE user_sign_up_policy (
  id          SMALLINT     PRIMARY KEY CHECK (id = 1),
  open        BOOLEAN      NOT NULL,
  changed_at  TIMESTAMPTZ  NOT NULL
);

-- 가입 정책 이력 — 추가만 한다. 처음 값은 이전 값이 없다
CREATE TABLE user_sign_up_policy_changes (
  id             BIGSERIAL    PRIMARY KEY,
  previous_open  BOOLEAN,
  open           BOOLEAN      NOT NULL,
  actor          TEXT         NOT NULL CHECK (actor IN ('SYSTEM', 'OPERATOR')),
  changed_at     TIMESTAMPTZ  NOT NULL
);

-- 초대 — 토큰은 다이제스트로만 둔다. 쓰거나 취소한 초대도 남는다. 쓴 초대의 계정이 지워지면 쓴 계정만 비고 쓴 시각은 남는다
CREATE TABLE user_invitations (
  id            BIGSERIAL    PRIMARY KEY,
  token_digest  BYTEA        NOT NULL UNIQUE,
  memo          TEXT,
  created_at    TIMESTAMPTZ  NOT NULL,
  expires_at    TIMESTAMPTZ  NOT NULL,
  revoked_at    TIMESTAMPTZ,
  used_at       TIMESTAMPTZ,
  used_by       BIGINT       REFERENCES users (id) ON DELETE SET NULL,
  version       BIGINT       NOT NULL,
  CHECK (revoked_at IS NULL OR used_at IS NULL),
  CHECK (used_by IS NULL OR used_at IS NOT NULL)
);
