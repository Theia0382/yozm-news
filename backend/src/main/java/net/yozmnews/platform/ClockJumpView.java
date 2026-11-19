package net.yozmnews.platform;

import java.time.Instant;
import java.util.Objects;

/**
 * 측정용 시계가 넘긴 구간 하나 — docs/specs/platform.md §3
 *
 * @param from 넘기기 전 측정 시각
 * @param to 넘긴 뒤 측정 시각
 */
public record ClockJumpView(Instant from, Instant to) {

	public ClockJumpView {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
	}

}
