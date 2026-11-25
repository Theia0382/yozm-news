package net.yozmnews.reader.adapter.in.web;

import net.yozmnews.report.ReportConditions;

/**
 * 리포트의 생성 조건 — 관심 설정 화면이 리포트 설정의 안내에 쓴다. docs/specs/reader.md reader-R17
 *
 * @param minDigestDays 주간 리포트를 만드는 데 필요한 그 주의 READY 다이제스트 날짜 수
 * @param minWeeklyReports 월간 리포트를 만드는 데 필요한 그 기간 안에 다 든 READY 주간 리포트 수
 */
record ReportConditionsResponse(int minDigestDays, int minWeeklyReports) {

	static ReportConditionsResponse of(ReportConditions conditions) {
		return new ReportConditionsResponse(conditions.minDigestDays(), conditions.minWeeklyReports());
	}

}
