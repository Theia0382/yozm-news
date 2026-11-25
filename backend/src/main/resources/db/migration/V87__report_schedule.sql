-- report 모듈 — 리포트 일정 · 생성 작업 · 거두기 · 다시 만들기(docs/specs/report.md §6).
-- dispatch_token 은 생성 작업의 ID(다시 등록 · 다시 만들기마다 새 값), dispatched_at 은 마지막 등록 시각, scheduled_at 은 생성을
-- 기다림이 된 시각(만들 때 · 다시 만들기를 받을 때 — 거두기의 시작 기한을 이 시각으로 잰다), regenerations 는 다시 만든 횟수다.
-- 기존 행은 새 토큰 · 만든 시각으로 채운다. 사용자 데이터를 지우지 않는다 — 같은 (사용자, 종류, 시작, 끝)의 행이 이미 둘 이상이면
-- 유일 인덱스가 만들어지지 않아 마이그레이션이 멈춘다.
ALTER TABLE reports
  ADD COLUMN dispatch_token UUID,
  ADD COLUMN dispatched_at  TIMESTAMPTZ,
  ADD COLUMN scheduled_at   TIMESTAMPTZ,
  ADD COLUMN regenerations  INTEGER NOT NULL DEFAULT 0 CHECK (regenerations >= 0);

UPDATE reports SET dispatch_token = gen_random_uuid(), dispatched_at = created_at, scheduled_at = created_at;

ALTER TABLE reports
  ALTER COLUMN dispatch_token SET NOT NULL,
  ALTER COLUMN dispatched_at SET NOT NULL,
  ALTER COLUMN scheduled_at SET NOT NULL,
  ALTER COLUMN regenerations DROP DEFAULT;

-- 시작하지 않은 실패(쓸 모델 없음 · 시작 기한 지남)를 허용한다 — 생성을 기다림은 시작 시각이 없고, 생성 중 · 내용 있음 · 재료 없음은
-- 시작 시각이 있다. 공급자는 시작 시각과 함께 있다. 끝난 행은 시간대가 있다(시작하지 않고 끝날 때도 그때의 시간대를 적는다).
ALTER TABLE reports
  DROP CONSTRAINT reports_started,
  ADD CONSTRAINT reports_started CHECK ((started_at IS NULL) = (provider IS NULL)
    AND (status <> 'SCHEDULED' OR started_at IS NULL)
    AND (status NOT IN ('RUNNING', 'READY', 'EMPTY') OR started_at IS NOT NULL)),
  DROP CONSTRAINT reports_zone,
  ADD CONSTRAINT reports_zone CHECK (status = 'SCHEDULED' OR zone IS NOT NULL);

CREATE UNIQUE INDEX reports_period_key ON reports (user_id, period_kind, period_start, period_end);
CREATE INDEX reports_unfinished_idx ON reports (status, user_id) WHERE status IN ('SCHEDULED', 'RUNNING');
