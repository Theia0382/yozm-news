package net.yozmnews.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import net.yozmnews.platform.AdvanceRefusal;
import net.yozmnews.platform.ClockAdvanceResult;
import net.yozmnews.platform.ClockJumpView;
import net.yozmnews.platform.MeasurementClockStatus;
import net.yozmnews.platform.domain.ClockJumps;
import net.yozmnews.platform.domain.MeasurementClockRows;
import net.yozmnews.platform.domain.MeasurementClockState;
import org.junit.jupiter.api.Test;

/**
 * 측정용 시계 — 출발 시각에서 실제 속도로 흐르고, 앞으로만 넘기며, 넘긴 구간을 먼저 저장한다. 실제 시간의 원천은 시험이 민다. 기대값은 손으로 셈한
 * 리터럴이다.
 * <p>
 * 출발 측정 시각 2026-11-09T22:05Z(11-10 07:05 KST), 출발에 대응하는 실제 시각 2026-11-19T03:00Z.
 */
class MeasurementClockTests {

	private static final Instant STARTED = Instant.parse("2026-11-09T22:05:00Z");

	private static final Instant ANCHORED = Instant.parse("2026-11-19T03:00:00Z");

	private final AtomicReference<Instant> real = new AtomicReference<>(ANCHORED);

	private final InstantSource realSource = this.real::get;

	private final RecordingRows rows = new RecordingRows();

	private MeasurementClock clock(ClockJumps jumps) {
		return new MeasurementClock(new MeasurementClockState(STARTED, ANCHORED, jumps), this.realSource, this.rows);
	}

	@Test
	void startsAtTheStartAndFlowsAtRealSpeed() {
		MeasurementClock clock = clock(ClockJumps.NONE);
		assertThat(clock.instant()).isEqualTo(STARTED);
		assertThat(clock.realTime()).isFalse();

		this.real.set(ANCHORED.plus(Duration.ofMinutes(10)));
		assertThat(clock.instant()).isEqualTo(Instant.parse("2026-11-09T22:15:00Z"));
	}

	@Test
	void advancingSavesTheJumpFirstAndThenMovesTheClock() {
		MeasurementClock clock = clock(ClockJumps.NONE);
		this.real.set(ANCHORED.plus(Duration.ofMinutes(10)));

		ClockAdvanceResult result = clock.advanceBy(Duration.ofHours(24));

		Instant from = Instant.parse("2026-11-09T22:15:00Z");
		Instant to = Instant.parse("2026-11-10T22:15:00Z");
		assertThat(this.rows.added).containsExactly(new ClockJumps.Jump(from, to));
		assertThat(result).isEqualTo(new ClockAdvanceResult.Advanced(
				new MeasurementClockStatus.On(to, STARTED, List.of(new ClockJumpView(from, to)))));
		assertThat(clock.instant()).isEqualTo(to);
		// 넘긴 구간은 경과에서 빠진다 — 넘기기 5분 전부터 넘긴 뒤 5분까지 10분
		this.real.set(ANCHORED.plus(Duration.ofMinutes(15)));
		assertThat(clock.elapsedSince(Instant.parse("2026-11-09T22:10:00Z"))).isEqualTo(Duration.ofMinutes(10));

		ClockAdvanceResult toArrival = clock.advanceTo(Instant.parse("2026-11-12T00:00:00Z"));
		assertThat(toArrival).isInstanceOf(ClockAdvanceResult.Advanced.class);
		assertThat(clock.instant()).isEqualTo(Instant.parse("2026-11-12T00:00:00Z"));
		assertThat(this.rows.added).hasSize(2);
	}

	@Test
	void advancesOnlyForward() {
		MeasurementClock clock = clock(ClockJumps.NONE);

		assertThat(clock.advanceBy(Duration.ZERO)).isEqualTo(new ClockAdvanceResult.Rejected(AdvanceRefusal.NOT_POSITIVE));
		assertThat(clock.advanceBy(Duration.ofMinutes(-1)))
			.isEqualTo(new ClockAdvanceResult.Rejected(AdvanceRefusal.NOT_POSITIVE));
		assertThat(clock.advanceTo(STARTED)).isEqualTo(new ClockAdvanceResult.Rejected(AdvanceRefusal.NOT_AHEAD));
		assertThat(clock.advanceTo(STARTED.minusSeconds(1)))
			.isEqualTo(new ClockAdvanceResult.Rejected(AdvanceRefusal.NOT_AHEAD));
		assertThat(this.rows.added).isEmpty();
		assertThat(clock.instant()).isEqualTo(STARTED);
	}

	@Test
	void aFailedSaveLeavesTheClockAsItWas() {
		this.rows.failing = true;
		MeasurementClock clock = clock(ClockJumps.NONE);

		assertThatIllegalStateException().isThrownBy(() -> clock.advanceBy(Duration.ofHours(24)));

		assertThat(clock.instant()).isEqualTo(STARTED);
		assertThat(clock.status().jumps()).isEmpty();
	}

	@Test
	void aRestartedClockShowsTheSameNowFromTheSavedJumps() {
		MeasurementClock first = clock(ClockJumps.NONE);
		first.advanceBy(Duration.ofHours(24));

		MeasurementClock restarted = clock(new ClockJumps(this.rows.added));

		assertThat(restarted.instant()).isEqualTo(first.instant()).isEqualTo(Instant.parse("2026-11-10T22:05:00Z"));
	}

	@Test
	void needsTheAnchor() {
		assertThatIllegalArgumentException().isThrownBy(() -> new MeasurementClock(
				new MeasurementClockState(STARTED, null, ClockJumps.NONE), this.realSource, this.rows));
	}

	/** 더한 구간을 남기는 저장소 대역 — {@code failing} 이면 더하기가 실패한다 */
	private static final class RecordingRows implements MeasurementClockRows {

		final List<ClockJumps.Jump> added = new ArrayList<>();

		boolean failing;

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
			if (this.failing) {
				throw new IllegalStateException("저장 실패");
			}
			this.added.add(jump);
		}

	}

}
