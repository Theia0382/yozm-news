package net.yozmnews.user.domain.interest;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 다이제스트 일정 — 시간대와 분 단위 생성 시각 — docs/specs/user.md §5 · user-R50
 * @param timezone 시간대(시간대 ID — {@link Timezones})
 * @param time 생성 시각 — 초 · 나노초는 0
 */
public record DigestSchedule(ZoneId timezone, LocalTime time) {

	/**
	 * @throws IllegalArgumentException 생성 시각에 초 · 나노초가 있을 때
	 */
	public DigestSchedule {
		Objects.requireNonNull(timezone, "timezone");
		Objects.requireNonNull(time, "time");
		if (time.getSecond() != 0 || time.getNano() != 0) {
			throw new IllegalArgumentException("생성 시각은 분 단위다: " + time);
		}
	}

}
