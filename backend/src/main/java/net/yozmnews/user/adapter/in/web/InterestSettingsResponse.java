package net.yozmnews.user.adapter.in.web;

import java.time.DayOfWeek;

import net.yozmnews.user.application.DigestSettings.DigestSettingsView;
import net.yozmnews.user.domain.interest.ReportSettings;

/**
 * 일정 · 리포트 조회. docs/specs/user.md §3 · user-R50
 *
 * @param timezone 시간대 ID
 * @param digestTime 생성 시각 — {@code HH:mm}
 * @param weeklyReport 주간 리포트를 받는가
 * @param weeklyReportDay 주간 리포트의 요일
 * @param monthlyReport 월간 리포트를 받는가
 * @param monthlyReportDay 월간 리포트의 날짜
 * @param monthlyReportDayRange 월간 리포트 날짜로 받는 범위 — 화면이 고를 값을 그린다
 */
record InterestSettingsResponse(String timezone, String digestTime, boolean weeklyReport,
		DayOfWeek weeklyReportDay, boolean monthlyReport, int monthlyReportDay,
		MonthlyReportDayRangeResponse monthlyReportDayRange) {

	static InterestSettingsResponse of(DigestSettingsView view) {
		return new InterestSettingsResponse(view.schedule().timezone().getId(), DigestTimes.format(view.schedule().time()),
				view.reports().weekly(), view.reports().weeklyDay(), view.reports().monthly(),
				view.reports().monthlyDay(),
				new MonthlyReportDayRangeResponse(ReportSettings.MONTHLY_DAY_MIN, ReportSettings.MONTHLY_DAY_MAX));
	}

	/**
	 * 월간 리포트 날짜로 받는 범위 — 경계 포함. 그 날이 없는 달은 그 달 마지막 날이다
	 * @param min 하한
	 * @param max 상한
	 */
	record MonthlyReportDayRangeResponse(int min, int max) {
	}

}
