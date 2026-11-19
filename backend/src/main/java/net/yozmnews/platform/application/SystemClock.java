package net.yozmnews.platform.application;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Objects;

import net.yozmnews.platform.AppClock;
import net.yozmnews.platform.domain.ClockJumps;

/**
 * 실제 시간의 앱 시계 — 운영 인스턴스의 {@link AppClock} · docs/specs/platform.md §4
 * <p>
 * 넘긴 기록이 없어 경과 판정은 단순한 차다 — 산식은 {@link ClockJumps#NONE} 에 맡긴다.
 */
public final class SystemClock implements AppClock {

	private final InstantSource real;

	/**
	 * @param real 실제 시간의 원천
	 */
	public SystemClock(InstantSource real) {
		this.real = Objects.requireNonNull(real, "real");
	}

	@Override
	public Instant instant() {
		return this.real.instant();
	}

	@Override
	public boolean realTime() {
		return true;
	}

	@Override
	public Duration elapsed(Instant from, Instant to) {
		return ClockJumps.NONE.elapsed(from, to);
	}

	@Override
	public Instant before(Instant at, Duration elapsed) {
		return ClockJumps.NONE.before(at, elapsed);
	}

	@Override
	public Instant after(Instant from, Duration elapsed) {
		return ClockJumps.NONE.after(from, elapsed);
	}

}
