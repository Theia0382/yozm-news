-- user 모듈 — 요청 제한의 창 셈(docs/specs/user.md §6 · user-R39 · user-R42).
--
-- 동작 · 축 · 대상(IP · 사용자 ID)의 SHA-256 다이제스트와 고정 창 시작마다 한 행이다 — 대상을 평문으로 두지 않는다. 창이 끝나면
-- 작업 역할의 반복 작업이 지운다.
CREATE TABLE request_limit_counts (
  limit_key     BYTEA        NOT NULL CHECK (octet_length(limit_key) = 32),
  window_start  TIMESTAMPTZ  NOT NULL,
  window_end    TIMESTAMPTZ  NOT NULL CHECK (window_end > window_start),
  requests      INTEGER      NOT NULL CHECK (requests >= 1),
  PRIMARY KEY (limit_key, window_start)
);
-- 끝난 창 정리가 끝 시각으로 찾는다
CREATE INDEX request_limit_counts_window_end_idx ON request_limit_counts (window_end);
