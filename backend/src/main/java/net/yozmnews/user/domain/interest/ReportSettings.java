package net.yozmnews.user.domain.interest;

import java.time.DayOfWeek;
import java.util.Objects;

/**
 * 변화 리포트 설정 — 저장 · 조회만 한다(일정은 리포트의 작업이 쓴다) — docs/specs/user.md §5 · user-R50
 * @param weekly 주간 리포트를 받는가
 * @param weeklyDay 주간 리포트의 요일
 * @param monthly 월간 리포트를 받는가
 */
public record ReportSettings(boolean weekly, DayOfWeek weeklyDay, boolean monthly) {

	public ReportSettings {
		Objects.requireNonNull(weeklyDay, "weeklyDay");
	}

}
