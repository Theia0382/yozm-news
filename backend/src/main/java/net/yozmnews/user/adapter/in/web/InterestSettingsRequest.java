package net.yozmnews.user.adapter.in.web;

import java.time.DayOfWeek;

import jakarta.validation.constraints.NotNull;

/**
 * 일정 · 리포트 저장 — 통째로 바꾼다. 칸이 없거나 요일 이름이 틀리면 본문 형식 400, 생성 시각이 {@code HH:mm} 이 아니면 400 이다.
 * docs/specs/user.md §3 · user-R50
 *
 * @param timezone 시간대 ID
 * @param digestTime 생성 시각 — {@code HH:mm}
 * @param weeklyReport 주간 리포트를 받는가
 * @param weeklyReportDay 주간 리포트의 요일
 * @param monthlyReport 월간 리포트를 받는가
 */
record InterestSettingsRequest(@NotNull String timezone, @NotNull String digestTime, @NotNull Boolean weeklyReport,
		@NotNull DayOfWeek weeklyReportDay, @NotNull Boolean monthlyReport) {
}
