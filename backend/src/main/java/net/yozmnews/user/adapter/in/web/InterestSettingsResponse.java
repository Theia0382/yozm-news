package net.yozmnews.user.adapter.in.web;

import java.time.DayOfWeek;

import net.yozmnews.user.application.DigestSettings.DigestSettingsView;

/**
 * 일정 · 리포트 조회. docs/specs/user.md §3 · user-R50
 *
 * @param timezone 시간대 ID
 * @param digestTime 생성 시각 — {@code HH:mm}
 * @param weeklyReport 주간 리포트를 받는가
 * @param weeklyReportDay 주간 리포트의 요일
 * @param monthlyReport 월간 리포트를 받는가
 */
record InterestSettingsResponse(String timezone, String digestTime, boolean weeklyReport,
		DayOfWeek weeklyReportDay, boolean monthlyReport) {

	static InterestSettingsResponse of(DigestSettingsView view) {
		return new InterestSettingsResponse(view.schedule().timezone().getId(), DigestTimes.format(view.schedule().time()),
				view.reports().weekly(), view.reports().weeklyDay(), view.reports().monthly());
	}

}
