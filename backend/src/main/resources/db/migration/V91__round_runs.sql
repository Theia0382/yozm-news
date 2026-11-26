-- 회차 기록 — 반복 회차가 끝날 때마다 한 행. 처리 현황이 최근 처리량과 마지막 회차를 읽는다(docs/specs/platform.md §6).
-- 보존 기간이 지난 행은 반복 작업이 지운다
CREATE TABLE round_runs (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  -- 회차 이름 — 소문자 낱말을 '-' 로 잇는다(코드의 정본 RoundRules.NAME 과 같은 꼴)
  round       TEXT         NOT NULL CHECK (round ~ '^[a-z]+(-[a-z]+)*$'),
  started_at  TIMESTAMPTZ  NOT NULL,
  ended_at    TIMESTAMPTZ  NOT NULL,
  -- 부른 뭉치 수 — 첫 뭉치가 예외를 던졌으면 0
  batches     INTEGER      NOT NULL CHECK (batches >= 0),
  handled     INTEGER      NOT NULL CHECK (handled >= 0),
  ended_by    TEXT         NOT NULL CHECK (ended_by IN ('DRAINED', 'BUDGET', 'BACKED_OFF', 'STOPPED', 'FAILED')),
  CHECK (ended_at >= started_at)
);

CREATE INDEX round_runs_round_ended_idx ON round_runs (round, ended_at);
