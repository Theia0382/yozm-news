package net.yozmnews.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.yozmnews.platform.AppClock;
import net.yozmnews.platform.domain.ActiveRoles;
import net.yozmnews.platform.domain.ClockJumps;
import net.yozmnews.platform.domain.MeasurementClockRows;
import net.yozmnews.platform.domain.MeasurementClockState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 앱 시계의 결선 — 시계 행 읽기를 대역으로 바꿔 profile 마다 컨텍스트를 띄워 본다. 기동 판정({@code ClockStart})의 거부가 기동 실패로 이어지는지,
 * 측정 profile 에서 처음 뜰 때 출발에 대응하는 실제 시각을 채우는지 본다.
 */
class ClockConfigurationTests {

	private static final Instant STARTED = Instant.parse("2026-11-09T22:05:00Z");

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(ClockConfiguration.class);

	@Test
	void withoutTheMeasurementProfileAndRowTheClockIsRealTime() {
		Rows rows = new Rows(null);
		this.runner.withBean(MeasurementClockRows.class, () -> rows).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(AppClock.class)).isInstanceOf(SystemClock.class);
			assertThat(context).doesNotHaveBean(MeasurementClock.class);
		});
	}

	@Test
	void aDatabaseWithTheRowDoesNotStartWithoutTheMeasurementProfile() {
		Rows rows = new Rows(new MeasurementClockState(STARTED, null, ClockJumps.NONE));
		this.runner.withBean(MeasurementClockRows.class, () -> rows).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("측정용 시계로 돈 DB");
		});
	}

	@Test
	void theMeasurementProfileAnchorsTheStartOnTheFirstStart() {
		Rows rows = new Rows(new MeasurementClockState(STARTED, null, ClockJumps.NONE));
		measuring(rows, new ActiveRoles(true, true)).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(AppClock.class)).isInstanceOf(MeasurementClock.class);
			assertThat(context).doesNotHaveBean(SystemClock.class);
			assertThat(rows.anchored).hasSize(1);
			// 대응 실제 시각을 지금으로 채웠으므로 지금은 출발 시각 근처다 — 실제 시간이 흐른 만큼 뒤
			assertThat(context.getBean(AppClock.class).instant()).isBetween(STARTED, STARTED.plusSeconds(60));
		});
	}

	@Test
	void theMeasurementProfileNeedsBothRoles() {
		Rows rows = new Rows(new MeasurementClockState(STARTED, null, ClockJumps.NONE));
		measuring(rows, new ActiveRoles(true, false)).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("web,worker,measurement");
			assertThat(rows.anchored).isEmpty();
		});
	}

	@Test
	void theMeasurementProfileNeedsTheRow() {
		measuring(new Rows(null), new ActiveRoles(true, true)).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("INSERT INTO measurement_clock (started_at)");
		});
	}

	private ApplicationContextRunner measuring(Rows rows, ActiveRoles roles) {
		return this.runner.withPropertyValues("spring.profiles.active=" + MeasurementOnly.PROFILE)
			.withBean(MeasurementClockRows.class, () -> rows)
			.withBean(ActiveRoles.class, () -> roles);
	}

	/** 시계 행 대역 — 행이 있으면 {@code anchor} 가 비어 있던 대응 실제 시각을 채운다 */
	private static final class Rows implements MeasurementClockRows {

		private @Nullable MeasurementClockState state;

		final List<Instant> anchored = new ArrayList<>();

		Rows(@Nullable MeasurementClockState state) {
			this.state = state;
		}

		@Override
		public boolean exists() {
			return this.state != null;
		}

		@Override
		public Optional<MeasurementClockState> load() {
			return Optional.ofNullable(this.state);
		}

		@Override
		public void anchor(Instant realNow) {
			if (this.state != null && this.state.anchoredAt() == null) {
				this.anchored.add(realNow);
				this.state = new MeasurementClockState(this.state.startedAt(), realNow, this.state.jumps());
			}
		}

		@Override
		public void addJump(ClockJumps.Jump jump) {
			throw new UnsupportedOperationException("이 시험은 넘기지 않는다");
		}

	}

}
