package net.yozmnews.user.domain.interest;

import java.time.DayOfWeek;
import java.util.Objects;

/**
 * 변화 리포트 설정 — 저장 · 조회만 한다(일정은 리포트의 작업이 쓴다) — docs/specs/user.md §5 · user-R50
 * <p>
 * 월간 날짜는 그 달의 날짜다. 그 날이 없는 달(31일을 고른 2월 등)의 경계는 리포트의 일정이 그 달 마지막 날로 정한다.
 * @param weekly 주간 리포트를 받는가
 * @param weeklyDay 주간 리포트의 요일
 * @param monthly 월간 리포트를 받는가
 * @param monthlyDay 월간 리포트의 날짜 — {@link #MONTHLY_DAY_MIN} ~ {@link #MONTHLY_DAY_MAX}
 */
public record ReportSettings(boolean weekly, DayOfWeek weeklyDay, boolean monthly, int monthlyDay) {

	/** 월간 날짜의 최소 — 정본 */
	public static final int MONTHLY_DAY_MIN = 1;

	/** 월간 날짜의 최대 — 정본 */
	public static final int MONTHLY_DAY_MAX = 31;

	/**
	 * @throws IllegalArgumentException 월간 날짜가 범위 밖일 때
	 */
	public ReportSettings {
		Objects.requireNonNull(weeklyDay, "weeklyDay");
		if (!validMonthlyDay(monthlyDay)) {
			throw new IllegalArgumentException("월간 날짜는 " + MONTHLY_DAY_MIN + " ~ " + MONTHLY_DAY_MAX + " 이다: " + monthlyDay);
		}
	}

	/** 월간 날짜로 받을 수 있는 값인가 */
	public static boolean validMonthlyDay(int day) {
		return day >= MONTHLY_DAY_MIN && day <= MONTHLY_DAY_MAX;
	}

}
