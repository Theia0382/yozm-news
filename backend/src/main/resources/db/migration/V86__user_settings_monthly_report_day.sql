-- user 모듈 — 월간 변화 리포트의 날짜(docs/specs/user.md §6).
-- monthly_report_day 는 그 달의 날짜(1 ~ 31)다 — 그 날이 없는 달은 리포트의 일정이 그 달 마지막 날로 정한다.
-- 칸을 더하기만 한다 — 기존 행은 기본값(1일)으로 채우고 기본값을 지운다. 새 행의 기본값은 코드(InterestDefaults)가 넣는다.
ALTER TABLE user_settings
  ADD COLUMN monthly_report_day SMALLINT NOT NULL DEFAULT 1 CHECK (monthly_report_day BETWEEN 1 AND 31);

ALTER TABLE user_settings
  ALTER COLUMN monthly_report_day DROP DEFAULT;
