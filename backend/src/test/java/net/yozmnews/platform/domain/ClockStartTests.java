package net.yozmnews.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * 앱 시계의 기동 판정 — 실제 시간의 시계는 시계 행이 없을 때만, 측정용 시계는 두 역할 · 시계 행이 함께 있을 때만 뜬다.
 */
class ClockStartTests {

	private static final String PROFILE = "measurement";

	private static final MeasurementClockState STATE = new MeasurementClockState(
			Instant.parse("2026-11-09T22:05:00Z"), null, ClockJumps.NONE);

	@Test
	void theRealTimeClockStartsOnlyWithoutAMeasurementRow() {
		assertThatNoException().isThrownBy(() -> ClockStart.requireNoMeasurementRow(false, PROFILE));
		assertThatIllegalStateException().isThrownBy(() -> ClockStart.requireNoMeasurementRow(true, PROFILE))
			.withMessageContaining("측정용 시계로 돈 DB")
			.withMessageContaining("profile measurement");
	}

	@Test
	void theMeasurementClockNeedsBothRolesAndTheRow() {
		assertThat(ClockStart.measurement(new ActiveRoles(true, true), Optional.of(STATE), PROFILE)).isEqualTo(STATE);
		assertThatIllegalStateException()
			.isThrownBy(() -> ClockStart.measurement(new ActiveRoles(true, true), Optional.empty(), PROFILE))
			.withMessageContaining("INSERT INTO measurement_clock (started_at)");
		assertThatIllegalStateException()
			.isThrownBy(() -> ClockStart.measurement(new ActiveRoles(true, false), Optional.of(STATE), PROFILE))
			.withMessageContaining("web,worker,measurement");
		assertThatIllegalStateException()
			.isThrownBy(() -> ClockStart.measurement(new ActiveRoles(false, true), Optional.of(STATE), PROFILE))
			.withMessageContaining("두 역할");
	}

}
