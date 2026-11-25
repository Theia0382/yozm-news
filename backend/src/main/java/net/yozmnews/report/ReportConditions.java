package net.yozmnews.report;

import net.yozmnews.report.domain.schedule.ReportScheduleRules;

/**
 * 리포트의 생성 조건 — 화면이 리포트 설정을 안내할 때 쓴다. 값의 정본은 일정 규칙({@code ReportScheduleRules})이다.
 * docs/specs/report.md §3 · report-R28
 *
 * @param minDigestDays 주간 리포트를 만드는 데 필요한 그 주의 READY 다이제스트 날짜 수
 * @param minWeeklyReports 월간 리포트를 만드는 데 필요한 그 기간 안에 다 든 READY 주간 리포트 수
 */
public record ReportConditions(int minDigestDays, int minWeeklyReports) {

	/** 지금 생성 조건 */
	public static ReportConditions current() {
		return new ReportConditions(ReportScheduleRules.MIN_DIGEST_DAYS, ReportScheduleRules.MIN_WEEKLY_REPORTS);
	}

}
