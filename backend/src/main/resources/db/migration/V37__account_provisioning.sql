-- user 모듈 — 계정을 만드는 길의 바닥: 초기 비밀번호의 강제 변경 표시와 복구 코드(docs/specs/user.md §6).

-- 강제 변경 — 남이 정한 초기 비밀번호로 만든 계정은 첫 로그인에서 비밀번호를 바꾸기 전까지 참이다. 있는 계정은 강제 변경이 아니다
ALTER TABLE user_password_credentials ADD COLUMN must_change BOOLEAN NOT NULL DEFAULT false;

-- 복구 코드 — 계정마다 많아야 하나이고 다이제스트로만 둔다. 쓰거나 다시 발급하면 같은 행을 새 코드로 바꾼다
CREATE TABLE user_recovery_codes (
  user_id      BIGINT       PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
  code_digest  BYTEA        NOT NULL,
  issued_at    TIMESTAMPTZ  NOT NULL
);
