package net.yozmnews.user;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

import net.yozmnews.platform.UserId;

/**
 * 사용자 하나의 리포트 일정 — {@link ReportScheduleDirectory} 가 낸다. docs/specs/user.md §3 · user-R50 · user-R76
 *
 * @param user 사용자
 * @param timezone 일정의 기준 시간대
 * @param time 다이제스트 생성 시각 — 분 단위. 리포트 기간의 경계 시각이다
 * @param weekly 주간 리포트를 받는가
 * @param weeklyDay 주간 리포트의 요일
 * @param monthly 월간 리포트를 받는가
 * @param monthlyDay 월간 리포트의 날짜 — 1 ~ 31, 그 날이 없는 달의 경계는 부르는 쪽이 정한다
 */
public record ReportScheduleEntry(UserId user, ZoneId timezone, LocalTime time, boolean weekly, DayOfWeek weeklyDay,
		boolean monthly, int monthlyDay) {

	public ReportScheduleEntry {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(timezone, "timezone");
		Objects.requireNonNull(time, "time");
		Objects.requireNonNull(weeklyDay, "weeklyDay");
	}

}
