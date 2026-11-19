package net.yozmnews.platform.application;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Objects;

import net.yozmnews.platform.AdvanceRefusal;
import net.yozmnews.platform.AppClock;
import net.yozmnews.platform.ClockAdvanceResult;
import net.yozmnews.platform.ClockJumpView;
import net.yozmnews.platform.MeasurementClockStatus;
import net.yozmnews.platform.domain.ClockJumps;
import net.yozmnews.platform.domain.MeasurementClockRows;
import net.yozmnews.platform.domain.MeasurementClockState;

/**
 * 측정용 시계 — 지정한 출발 시각에서 실제 속도로 흐르고, 운영자가 앞으로 넘긴다 · docs/specs/platform.md §4
 * <p>
 * 지금 = 출발 시각 + (실제 지금 − 출발에 대응하는 실제 시각) + 넘긴 길이의 합이다. 내려 있던 동안의 실제 시간도 흐른 것으로 친다. 넘길 때는 넘긴
 * 구간을 DB 에 먼저 쓰고 메모리의 넘긴 기록을 바꾼다 — 쓰기가 실패하면 메모리는 그대로다. 경과 판정은 넘긴 기록({@link ClockJumps})에 맡긴다.
 * 넘기기 규칙(길이는 0 보다 길고 도착 시각은 지금보다 뒤)은 넘기는 순간의 지금으로 여기서 판정한다.
 */
public final class MeasurementClock implements AppClock {

	private final Instant startedAt;

	private final Instant anchoredAt;

	private final InstantSource real;

	private final MeasurementClockRows rows;

	private volatile ClockJumps jumps;

	/**
	 * @param state 저장된 상태 — 출발에 대응하는 실제 시각이 있어야 한다
	 * @param real 실제 시간의 원천
	 * @param rows 넘긴 구간을 쓸 곳
	 * @throws IllegalArgumentException 출발에 대응하는 실제 시각이 없을 때
	 */
	public MeasurementClock(MeasurementClockState state, InstantSource real, MeasurementClockRows rows) {
		Objects.requireNonNull(state, "state");
		if (state.anchoredAt() == null) {
			throw new IllegalArgumentException("측정용 시계의 출발에 대응하는 실제 시각이 없다");
		}
		this.startedAt = state.startedAt();
		this.anchoredAt = state.anchoredAt();
		this.jumps = state.jumps();
		this.real = Objects.requireNonNull(real, "real");
		this.rows = Objects.requireNonNull(rows, "rows");
	}

	@Override
	public Instant instant() {
		return this.startedAt.plus(Duration.between(this.anchoredAt, this.real.instant())).plus(this.jumps.skipped());
	}

	@Override
	public boolean realTime() {
		return false;
	}

	@Override
	public Duration elapsed(Instant from, Instant to) {
		return this.jumps.elapsed(from, to);
	}

	@Override
	public Instant before(Instant at, Duration elapsed) {
		return this.jumps.before(at, elapsed);
	}

	@Override
	public Instant after(Instant from, Duration elapsed) {
		return this.jumps.after(from, elapsed);
	}

	/** 지금의 상태 */
	public MeasurementClockStatus.On status() {
		return new MeasurementClockStatus.On(instant(), this.startedAt,
				this.jumps.jumps().stream().map(jump -> new ClockJumpView(jump.from(), jump.to())).toList());
	}

	/**
	 * {@code by} 만큼 앞으로 넘긴다.
	 * @return 길이가 0 이하면 {@link AdvanceRefusal#NOT_POSITIVE}, 아니면 넘긴 뒤의 상태
	 */
	public synchronized ClockAdvanceResult advanceBy(Duration by) {
		Objects.requireNonNull(by, "by");
		if (by.isNegative() || by.isZero()) {
			return new ClockAdvanceResult.Rejected(AdvanceRefusal.NOT_POSITIVE);
		}
		Instant from = instant();
		return jump(from, from.plus(by));
	}

	/**
	 * {@code to} 까지 넘긴다.
	 * @return 도착 시각이 지금보다 뒤가 아니면 {@link AdvanceRefusal#NOT_AHEAD}, 아니면 넘긴 뒤의 상태
	 */
	public synchronized ClockAdvanceResult advanceTo(Instant to) {
		Objects.requireNonNull(to, "to");
		Instant from = instant();
		if (!to.isAfter(from)) {
			return new ClockAdvanceResult.Rejected(AdvanceRefusal.NOT_AHEAD);
		}
		return jump(from, to);
	}

	private ClockAdvanceResult jump(Instant from, Instant to) {
		ClockJumps.Jump jump = new ClockJumps.Jump(from, to);
		ClockJumps next = this.jumps.with(jump);
		this.rows.addJump(jump);
		this.jumps = next;
		return new ClockAdvanceResult.Advanced(status());
	}

}
