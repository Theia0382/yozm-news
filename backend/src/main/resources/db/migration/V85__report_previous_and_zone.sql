-- 변화 리포트 행이 생성 때 이은 직전 리포트와 기간의 날짜를 적은 시간대를 남긴다(docs/specs/report.md §6).
-- 같은 모듈 안의 자기 참조라 FK 를 둔다 — 직전 리포트가 지워지면 칸만 비운다.
-- 시간대 CHECK 는 스냅숏 CHECK(reports_snapshot)와 같은 한쪽 조건이다 — 시작 저장 직전에 스냅숏 · 직전 리포트 · 시간대를 쓰는 순서에서 성립한다.
-- 시작한 옛 행이 있으면 시간대가 없어 CHECK 가 실패해 드러난다 — 옛 행의 시간대를 짐작해 채우지 않는다.
ALTER TABLE reports
  ADD COLUMN previous_report_id BIGINT REFERENCES reports (id) ON DELETE SET NULL,
  ADD COLUMN zone TEXT,
  ADD CONSTRAINT reports_previous_not_self CHECK (previous_report_id <> id),
  ADD CONSTRAINT reports_zone CHECK (started_at IS NULL OR zone IS NOT NULL);
DROP INDEX reports_user_id_idx;
CREATE INDEX reports_user_kind_id_idx ON reports (user_id, period_kind, id DESC);
