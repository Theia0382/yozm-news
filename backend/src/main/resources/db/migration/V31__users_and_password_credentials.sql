-- user 모듈 — 계정과 자체 로그인 자격(docs/specs/user.md §6).

-- 계정 — 이메일은 정규화한 값이고 고유 제약이 계정의 고유 키다(user-R13). 비밀번호 해시는 두지 않는다(user-R16)
CREATE TABLE users (
  id            BIGSERIAL    PRIMARY KEY,
  email         TEXT         NOT NULL UNIQUE,
  display_name  TEXT         NOT NULL,
  role          TEXT         NOT NULL CHECK (role IN ('USER', 'ADMIN')),
  status        TEXT         NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
  created_at    TIMESTAMPTZ  NOT NULL,
  deleted_at    TIMESTAMPTZ,
  version       BIGINT       NOT NULL
);

-- 자체 로그인 자격 — 계정마다 하나, 계정을 지우면 함께 지워진다(user-R16). 해시는 파라미터를 담은 Argon2id 문자열(user-R15)
CREATE TABLE user_password_credentials (
  user_id              BIGINT       PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
  password_hash        TEXT         NOT NULL,
  password_changed_at  TIMESTAMPTZ  NOT NULL,
  version              BIGINT       NOT NULL
);
