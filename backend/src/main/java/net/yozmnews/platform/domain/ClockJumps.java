package net.yozmnews.platform.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 앱의 시계가 넘긴 기록과 경과 산식 — 정본 · docs/specs/platform.md §4
 * <p>
 * 넘긴 구간 하나는 넘기기 전 시각 {@code from} 부터 넘긴 뒤 시각 {@code to} 까지다. 두 시각 사이의 경과는 그 사이의 길이에서 넘긴 구간과 겹친
 * 길이를 뺀 것이다 — {@code elapsed(t1, t2) = (t2 − t1) − (넘긴 구간들과 [t1, t2] 의 겹친 길이)}. 넘긴 구간 안의 시각에는 앱이 아무것도
 * 기록하지 않으므로, 경과는 기록된 시각 위에서 연속이고 끝 시각이 같으면 시작 시각이 늦을수록 작다. 넘긴 기록이 없으면 경과는 단순한 차다.
 *
 * @param jumps 넘긴 구간 — 시간 순이고 겹치지 않는다
 */
public record ClockJumps(List<Jump> jumps) {

	/** 넘긴 기록이 없다 — 실제 시간의 시계 */
	public static final ClockJumps NONE = new ClockJumps(List.of());

	/**
	 * @throws IllegalArgumentException 구간이 시간 순이 아니거나 겹칠 때
	 */
	public ClockJumps {
		jumps = List.copyOf(Objects.requireNonNull(jumps, "jumps"));
		for (int i = 1; i < jumps.size(); i++) {
			if (jumps.get(i).from().isBefore(jumps.get(i - 1).to())) {
				throw new IllegalArgumentException("넘긴 구간은 시간 순이고 겹치지 않는다 — " + jumps.get(i - 1) + " 뒤에 " + jumps.get(i));
			}
		}
	}

	/** 넘긴 길이의 합 */
	public Duration skipped() {
		Duration total = Duration.ZERO;
		for (Jump jump : this.jumps) {
			total = total.plus(jump.length());
		}
		return total;
	}

	/** {@code from} 부터 {@code to} 까지 넘긴 시간을 뺀 경과 — {@code to} 가 {@code from} 보다 앞서거나 같으면 0 */
	public Duration elapsed(Instant from, Instant to) {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		if (!to.isAfter(from)) {
			return Duration.ZERO;
		}
		Duration elapsed = Duration.between(from, to);
		for (Jump jump : this.jumps) {
			Instant start = later(from, jump.from());
			Instant end = earlier(to, jump.to());
			if (end.isAfter(start)) {
				elapsed = elapsed.minus(Duration.between(start, end));
			}
		}
		return elapsed;
	}

	/**
	 * {@code at} 까지의 경과가 {@code elapsed} 이상인 가장 늦은 시각 — 그 시각과 그보다 앞선 시각은 {@code at} 까지 {@code elapsed} 이상
	 * 지났다. 넘긴 기록이 없으면 {@code at − elapsed} 다.
	 * @throws IllegalArgumentException {@code elapsed} 가 음수일 때
	 */
	public Instant before(Instant at, Duration elapsed) {
		Objects.requireNonNull(at, "at");
		requireNotNegative(elapsed);
		if (elapsed.isZero()) {
			return at;
		}
		Instant cursor = at;
		Duration remaining = elapsed;
		for (int i = this.jumps.size() - 1; i >= 0; i--) {
			Jump jump = this.jumps.get(i);
			if (!jump.from().isBefore(cursor)) {
				continue;
			}
			if (jump.to().isBefore(cursor)) {
				Duration span = Duration.between(jump.to(), cursor);
				if (remaining.compareTo(span) <= 0) {
					return cursor.minus(remaining);
				}
				remaining = remaining.minus(span);
			}
			cursor = jump.from();
		}
		return cursor.minus(remaining);
	}

	/**
	 * {@code from} 부터의 경과가 {@code elapsed} 가 되는 가장 이른 시각 — 지금까지의 넘긴 기록으로 계산한다. 넘긴 기록이 없으면
	 * {@code from + elapsed} 다.
	 * @throws IllegalArgumentException {@code elapsed} 가 음수일 때
	 */
	public Instant after(Instant from, Duration elapsed) {
		Objects.requireNonNull(from, "from");
		requireNotNegative(elapsed);
		if (elapsed.isZero()) {
			return from;
		}
		Instant cursor = from;
		Duration remaining = elapsed;
		for (Jump jump : this.jumps) {
			if (!jump.to().isAfter(cursor)) {
				continue;
			}
			if (jump.from().isAfter(cursor)) {
				Duration span = Duration.between(cursor, jump.from());
				if (remaining.compareTo(span) <= 0) {
					return cursor.plus(remaining);
				}
				remaining = remaining.minus(span);
			}
			cursor = jump.to();
		}
		return cursor.plus(remaining);
	}

	/**
	 * 끝에 넘긴 구간 하나를 더한다.
	 * @throws IllegalArgumentException 새 구간이 마지막 구간보다 앞설 때
	 */
	public ClockJumps with(Jump next) {
		Objects.requireNonNull(next, "next");
		List<Jump> extended = new ArrayList<>(this.jumps);
		extended.add(next);
		return new ClockJumps(extended);
	}

	private static void requireNotNegative(Duration elapsed) {
		Objects.requireNonNull(elapsed, "elapsed");
		if (elapsed.isNegative()) {
			throw new IllegalArgumentException("경과는 음수가 아니다: " + elapsed);
		}
	}

	private static Instant later(Instant a, Instant b) {
		return a.isAfter(b) ? a : b;
	}

	private static Instant earlier(Instant a, Instant b) {
		return a.isBefore(b) ? a : b;
	}

	/**
	 * 넘긴 구간 하나.
	 *
	 * @param from 넘기기 전 시각
	 * @param to 넘긴 뒤 시각
	 */
	public record Jump(Instant from, Instant to) {

		/**
		 * @throws IllegalArgumentException {@code to} 가 {@code from} 보다 뒤가 아닐 때
		 */
		public Jump {
			Objects.requireNonNull(from, "from");
			Objects.requireNonNull(to, "to");
			if (!to.isAfter(from)) {
				throw new IllegalArgumentException("넘긴 뒤 시각은 넘기기 전 시각보다 뒤다: " + from + " → " + to);
			}
		}

		/** 넘긴 길이 */
		public Duration length() {
			return Duration.between(this.from, this.to);
		}

	}

}
