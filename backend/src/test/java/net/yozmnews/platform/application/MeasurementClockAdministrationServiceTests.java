package net.yozmnews.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import net.yozmnews.platform.AdvanceRefusal;
import net.yozmnews.platform.ClockAdvanceResult;
import net.yozmnews.platform.ClockJumpView;
import net.yozmnews.platform.MeasurementClockStatus;
import net.yozmnews.platform.domain.ClockJumps;
import net.yozmnews.platform.domain.MeasurementClockRows;
import net.yozmnews.platform.domain.MeasurementClockState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/**
 * 측정용 시계 운영 API — 측정용 시계가 없으면 Off, 있으면 상태를 내고 넘긴다. 넘길 길이 · 도착 시각을 둘 다 받거나 둘 다 받지 않으면 거부한다.
 */
class MeasurementClockAdministrationServiceTests {

	private static final Instant NOW = Instant.parse("2026-11-09T22:05:00Z");

	private final MeasurementClock clock = new MeasurementClock(new MeasurementClockState(NOW, NOW, ClockJumps.NONE),
			() -> NOW, new NoRows());

	private MeasurementClockAdministrationService service(boolean measuring) {
		StaticListableBeanFactory beans = new StaticListableBeanFactory();
		if (measuring) {
			beans.addBean("measurementClock", this.clock);
		}
		return new MeasurementClockAdministrationService(beans.getBeanProvider(MeasurementClock.class));
	}

	@Test
	void withoutTheMeasurementClockBothOperationsAreOff() {
		MeasurementClockAdministrationService service = service(false);

		assertThat(service.status()).isEqualTo(new MeasurementClockStatus.Off());
		assertThat(service.advance(Duration.ofHours(24), null)).isEqualTo(new ClockAdvanceResult.Off());
		assertThat(service.advance(null, null)).isEqualTo(new ClockAdvanceResult.Off());
	}

	@Test
	void takesExactlyOneOfLengthAndArrival() {
		MeasurementClockAdministrationService service = service(true);

		assertThat(service.advance(Duration.ofHours(1), NOW.plusSeconds(60)))
			.isEqualTo(new ClockAdvanceResult.Rejected(AdvanceRefusal.BOTH_GIVEN));
		assertThat(service.advance(null, null)).isEqualTo(new ClockAdvanceResult.Rejected(AdvanceRefusal.NONE_GIVEN));
		assertThat(service.advance(Duration.ZERO, null))
			.isEqualTo(new ClockAdvanceResult.Rejected(AdvanceRefusal.NOT_POSITIVE));
		assertThat(service.advance(null, NOW)).isEqualTo(new ClockAdvanceResult.Rejected(AdvanceRefusal.NOT_AHEAD));
		assertThat(service.status()).isEqualTo(new MeasurementClockStatus.On(NOW, NOW, List.of()));
	}

	@Test
	void advancesTheMeasurementClock() {
		MeasurementClockAdministrationService service = service(true);
		Instant later = Instant.parse("2026-11-10T22:05:00Z");

		assertThat(service.advance(Duration.ofHours(24), null)).isEqualTo(new ClockAdvanceResult.Advanced(
				new MeasurementClockStatus.On(later, NOW, List.of(new ClockJumpView(NOW, later)))));
		assertThat(service.advance(null, Instant.parse("2026-11-11T00:00:00Z")))
			.isInstanceOf(ClockAdvanceResult.Advanced.class);
	}

	/** 아무것도 하지 않는 저장소 대역 */
	private static final class NoRows implements MeasurementClockRows {

		@Override
		public boolean exists() {
			return true;
		}

		@Override
		public Optional<MeasurementClockState> load() {
			return Optional.empty();
		}

		@Override
		public void anchor(Instant realNow) {
		}

		@Override
		public void addJump(ClockJumps.Jump jump) {
		}

	}

}
