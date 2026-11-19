package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.platform.ClockJumpView;
import net.yozmnews.platform.MeasurementClockStatus;

/**
 * 측정용 시계의 상태 — 지금의 측정 시각, 출발 시각, 넘긴 기록(시간 순). docs/specs/admin.md §3
 */
record MeasurementClockResponse(Instant now, Instant startedAt, List<ClockJumpResponse> jumps) {

	static MeasurementClockResponse of(MeasurementClockStatus.On status) {
		return new MeasurementClockResponse(status.now(), status.startedAt(),
				status.jumps().stream().map(ClockJumpResponse::of).toList());
	}

	/** 넘긴 구간 하나 — 넘기기 전 · 넘긴 뒤 측정 시각 */
	record ClockJumpResponse(Instant from, Instant to) {

		static ClockJumpResponse of(ClockJumpView jump) {
			return new ClockJumpResponse(jump.from(), jump.to());
		}

	}

}
