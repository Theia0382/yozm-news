package net.yozmnews.user.domain.interest;

import java.util.Objects;

/**
 * 설정 행의 일정 · 리포트 설정 — 다이제스트 · 리포트의 일정 목록이 함께 읽는다. docs/specs/user.md §4 · user-R61 · user-R76
 *
 * @param schedule 일정(시간대 · 다이제스트 생성 시각)
 * @param reports 리포트 설정
 */
public record ScheduleSettings(DigestSchedule schedule, ReportSettings reports) {

	/** 설정 행이 없는 계정의 값 — 기본 일정 · 기본 리포트 설정({@link InterestDefaults}) */
	public static final ScheduleSettings DEFAULTS = new ScheduleSettings(InterestDefaults.SCHEDULE,
			InterestDefaults.REPORTS);

	public ScheduleSettings {
		Objects.requireNonNull(schedule, "schedule");
		Objects.requireNonNull(reports, "reports");
	}

}
