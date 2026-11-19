package net.yozmnews.platform;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 측정용 시계의 상태 — docs/specs/platform.md §3 */
public sealed interface MeasurementClockStatus {

	/** 앱의 시계가 측정용 시계가 아니다 — 실제 시간이다 */
	record Off() implements MeasurementClockStatus {
	}

	/**
	 * 측정용 시계가 켜져 있다.
	 *
	 * @param now 지금의 측정 시각
	 * @param startedAt 측정 시각의 출발점
	 * @param jumps 넘긴 기록 — 시간 순
	 */
	record On(Instant now, Instant startedAt, List<ClockJumpView> jumps) implements MeasurementClockStatus {

		public On {
			Objects.requireNonNull(now, "now");
			Objects.requireNonNull(startedAt, "startedAt");
			jumps = List.copyOf(Objects.requireNonNull(jumps, "jumps"));
		}

	}

}
