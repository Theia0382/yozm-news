package net.yozmnews.platform.domain;

import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 측정용 시계의 저장된 상태 — docs/specs/platform.md §4
 *
 * @param startedAt 측정 시각의 출발점
 * @param anchoredAt 출발점에 대응하는 실제 시각 — 처음 뜨기 전이면 null
 * @param jumps 넘긴 기록 — 시각은 측정 시각이다
 */
public record MeasurementClockState(Instant startedAt, @Nullable Instant anchoredAt, ClockJumps jumps) {

	public MeasurementClockState {
		Objects.requireNonNull(startedAt, "startedAt");
		Objects.requireNonNull(jumps, "jumps");
	}

}
