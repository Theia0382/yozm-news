package net.yozmnews.user.domain.interest;

import java.util.Objects;

import net.yozmnews.platform.UserId;

/**
 * 쓰는 계정 하나의 다이제스트 일정 — 일정 목록 조회의 저장소 결과. docs/specs/user.md §4
 */
public record ScheduledUser(UserId user, DigestSchedule schedule) {

	public ScheduledUser {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(schedule, "schedule");
	}

}
