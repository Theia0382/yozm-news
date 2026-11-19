package net.yozmnews;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Objects;

import net.yozmnews.platform.AppClock;
import net.yozmnews.platform.domain.ClockJumps;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 시험 시계 — 고정 시계와, 실제로 흐르거나 넘길 수 있는 시계. 경과 산식은 main 의 {@link ClockJumps} 를 그대로 쓴다(시험이 산식을 다시 셈하지
 * 않는다).
 */
public final class TestClocks {

	private TestClocks() {
	}

	/** 지금이 언제나 {@code now} 인 실제 시간의 시계 */
	public static AppClock fixed(Instant now) {
		return of(InstantSource.fixed(Objects.requireNonNull(now, "now")));
	}

	/** 지금을 {@code source} 에서 읽는 실제 시간의 시계 — 넘긴 기록이 없다. 시험이 지금을 바꾸는 원천을 그대로 쓴다 */
	public static AppClock of(InstantSource source) {
		Objects.requireNonNull(source, "source");
		return new SourceClock(source);
	}

	/** {@code start} 에서 출발해 흐르거나 넘길 수 있는 측정용 시계의 흉내 — {@link AppClock#realTime()} 거짓 */
	public static MovableClock movable(Instant start) {
		return new MovableClock(Objects.requireNonNull(start, "start"));
	}

	/**
	 * 앱 컨텍스트를 잘라 띄우는 시험이 넣는 시계 bean — 지금은 {@link #CONTEXT_NOW} 다.
	 * <p>
	 * 선언 타입이 {@link InstantSource} 다 — Spring Modulith 의 모듈 시험은 띄우지 않은 모듈(여기서는 {@code platform})의 타입으로 선언된 bean
	 * 정의를 지운다.
	 */
	@TestConfiguration(proxyBeanMethods = false)
	public static class FixedClockConfiguration {

		/** 시험 구성 시계의 지금 */
		public static final Instant CONTEXT_NOW = Instant.parse("2026-11-19T00:00:00Z");

		@Bean
		InstantSource testClock() {
			return fixed(CONTEXT_NOW);
		}

	}

	/**
	 * 앱 컨텍스트를 잘라 띄우는 시험이 넣는 시계 bean 가운데 선언 타입이 {@link AppClock} 인 것 — {@code AppClock} 으로 받는 구성요소를 만드는
	 * 컨텍스트가 쓴다. 지금은 {@link FixedClockConfiguration#CONTEXT_NOW} 다. Spring Modulith 의 모듈 시험에서는 {@code platform} 타입이라
	 * 지워지므로 그곳에는 {@link FixedClockConfiguration} 을 쓴다.
	 */
	@TestConfiguration(proxyBeanMethods = false)
	public static class FixedAppClockConfiguration {

		@Bean
		AppClock testAppClock() {
			return fixed(FixedClockConfiguration.CONTEXT_NOW);
		}

	}

	private record SourceClock(InstantSource source) implements AppClock {

		@Override
		public Instant instant() {
			return this.source.instant();
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

	/**
	 * 흐르거나 넘길 수 있는 시계 — {@link #advance} 는 실제 시간이 흐른 것처럼 지금만 밀고, {@link #jump} 는 넘긴 기록을 더한다.
	 */
	public static final class MovableClock implements AppClock {

		private Instant now;

		private ClockJumps jumps = ClockJumps.NONE;

		private MovableClock(Instant start) {
			this.now = start;
		}

		/** 실제 시간이 {@code duration} 흐른다 */
		public synchronized MovableClock advance(Duration duration) {
			this.now = this.now.plus(duration);
			return this;
		}

		/** 시계를 {@code duration} 앞으로 넘긴다 — 넘긴 기록이 남는다 */
		public synchronized MovableClock jump(Duration duration) {
			Instant to = this.now.plus(duration);
			this.jumps = this.jumps.with(new ClockJumps.Jump(this.now, to));
			this.now = to;
			return this;
		}

		@Override
		public synchronized Instant instant() {
			return this.now;
		}

		@Override
		public boolean realTime() {
			return false;
		}

		@Override
		public synchronized Duration elapsed(Instant from, Instant to) {
			return this.jumps.elapsed(from, to);
		}

		@Override
		public synchronized Instant before(Instant at, Duration elapsed) {
			return this.jumps.before(at, elapsed);
		}

		@Override
		public synchronized Instant after(Instant from, Duration elapsed) {
			return this.jumps.after(from, elapsed);
		}

	}

}
