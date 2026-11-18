package net.yozmnews.user;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

import net.yozmnews.platform.UserId;

/**
 * 사용자 하나의 다이제스트 일정 — {@link DigestScheduleDirectory} 가 낸다. docs/specs/user.md §3
 *
 * @param user 사용자
 * @param timezone 일정의 기준 시간대(user-R50)
 * @param time 생성 시각 — 분 단위
 */
public record DigestScheduleEntry(UserId user, ZoneId timezone, LocalTime time) {

	public DigestScheduleEntry {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(timezone, "timezone");
		Objects.requireNonNull(time, "time");
	}

}
