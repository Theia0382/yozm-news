package net.yozmnews.platform.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import net.yozmnews.platform.AdvanceRefusal;
import net.yozmnews.platform.ClockAdvanceResult;
import net.yozmnews.platform.MeasurementClockAdministration;
import net.yozmnews.platform.MeasurementClockStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * {@link MeasurementClockAdministration} 의 구현 — docs/specs/platform.md §4
 * <p>
 * 측정용 시계 bean 이 없으면(측정 profile 이 아니면) 두 연산 모두 {@code Off} 다. 넘길 길이 · 도착 시각을 둘 다 받거나 둘 다 받지 않으면
 * 거부하고, 나머지 넘기기 규칙은 측정용 시계가 넘기는 순간에 판정한다. 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class MeasurementClockAdministrationService implements MeasurementClockAdministration {

	private final ObjectProvider<MeasurementClock> clocks;

	MeasurementClockAdministrationService(ObjectProvider<MeasurementClock> clocks) {
		this.clocks = Objects.requireNonNull(clocks, "clocks");
	}

	@Override
	public MeasurementClockStatus status() {
		@Nullable MeasurementClock clock = this.clocks.getIfAvailable();
		return (clock == null) ? new MeasurementClockStatus.Off() : clock.status();
	}

	@Override
	public ClockAdvanceResult advance(@Nullable Duration by, @Nullable Instant to) {
		@Nullable MeasurementClock clock = this.clocks.getIfAvailable();
		if (clock == null) {
			return new ClockAdvanceResult.Off();
		}
		if (by != null && to != null) {
			return new ClockAdvanceResult.Rejected(AdvanceRefusal.BOTH_GIVEN);
		}
		if (by != null) {
			return clock.advanceBy(by);
		}
		if (to != null) {
			return clock.advanceTo(to);
		}
		return new ClockAdvanceResult.Rejected(AdvanceRefusal.NONE_GIVEN);
	}

}
