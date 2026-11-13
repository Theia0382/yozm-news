-- user 모듈 — 로그인 실패 기록(docs/specs/user.md §6 · user-R18 · user-R19).
--
-- 제출된 이메일을 정규화한 값의 SHA-256 다이제스트마다 한 행이다 — 계정 유무와 무관하다. 실패 0회 · 시각 없음은 한 시도의
-- 트랜잭션 안에서만 있는 자리 행이다(시도가 행을 잠그려고 먼저 넣는다) — 같은 트랜잭션이 실패를 기록하거나 행을 지워 끝낸다.
CREATE TABLE login_failures (
  email_digest    BYTEA        PRIMARY KEY,
  failures        INTEGER      NOT NULL CHECK (failures >= 0),
  last_failed_at  TIMESTAMPTZ,
  CHECK ((failures = 0) = (last_failed_at IS NULL))
);
-- 오래된 기록 정리가 마지막 실패 시각으로 찾는다
CREATE INDEX login_failures_last_failed_at_idx ON login_failures (last_failed_at);
