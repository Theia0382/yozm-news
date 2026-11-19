package net.yozmnews.platform;

import java.time.Duration;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * 측정용 시계 운영 API — 측정용 시계의 상태를 보고 앞으로 넘긴다 · docs/specs/platform.md §3
 * <p>
 * 언제나 쓸 수 있다. 앱의 시계가 측정용 시계가 아니면(운영 인스턴스) 두 연산 모두 {@code Off} 를 내고 아무것도 바꾸지 않는다.
 */
public interface MeasurementClockAdministration {

	/** 측정용 시계의 상태 — 측정용 시계가 아니면 {@link MeasurementClockStatus.Off} */
	MeasurementClockStatus status();

	/**
	 * 앞으로 넘긴다 — {@code by} · {@code to} 가운데 정확히 하나를 받는다.
	 * @param by 넘길 길이 — 0 보다 길어야 한다
	 * @param to 도착 시각 — 지금보다 뒤여야 한다
	 */
	ClockAdvanceResult advance(@Nullable Duration by, @Nullable Instant to);

}
