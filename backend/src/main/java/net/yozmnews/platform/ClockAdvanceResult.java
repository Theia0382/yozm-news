package net.yozmnews.platform;

import java.util.Objects;

/** 측정용 시계를 넘긴 결과 — docs/specs/platform.md §3 */
public sealed interface ClockAdvanceResult {

	/** 앱의 시계가 측정용 시계가 아니라 넘기지 않았다 */
	record Off() implements ClockAdvanceResult {
	}

	/**
	 * 인자가 틀려 넘기지 않았다.
	 *
	 * @param reason 틀린 까닭
	 */
	record Rejected(AdvanceRefusal reason) implements ClockAdvanceResult {

		public Rejected {
			Objects.requireNonNull(reason, "reason");
		}

	}

	/**
	 * 넘겼다.
	 *
	 * @param status 넘긴 뒤의 상태
	 */
	record Advanced(MeasurementClockStatus.On status) implements ClockAdvanceResult {

		public Advanced {
			Objects.requireNonNull(status, "status");
		}

	}

}
