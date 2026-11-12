package net.yozmnews.user.domain.throttle;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 로그인 실패 지연 일정의 정본 — docs/specs/user.md §4 · user-R19
 * <p>
 * 마지막 실패에서 {@link #FORGET_AFTER} 안의 연속 실패가 n 회면, 다시 시도할 수 있는 때는 마지막 실패 시각 + 대기다. 대기는 n ≤
 * {@link #FREE_FAILURES} 이면 0, 그 밖 {@code min(FIRST_WAIT × 2^(n−4), MAX_WAIT)}. 그보다 오래된 기록은 실패 0 회로 본다.
 */
public final class LoginThrottle {

	/** 지연 없는 연속 실패 수 */
	public static final int FREE_FAILURES = 3;

	/** 첫 대기 — 지연 없는 실패 다음 실패의 대기 */
	public static final Duration FIRST_WAIT = Duration.ofSeconds(1);

	/** 대기 상한 */
	public static final Duration MAX_WAIT = Duration.ofMinutes(15);

	/** 이만큼 실패가 없으면 기록을 잊는다 */
	public static final Duration FORGET_AFTER = Duration.ofHours(24);

	/** 두 배씩 늘리는 횟수의 상한 — 넘어도 상한 대기이고, 시프트가 넘치지 않게 막는다 */
	private static final int MAX_DOUBLINGS = 30;

	private LoginThrottle() {
	}

	/**
	 * 지금 기다려야 하면 다시 시도할 수 있는 때.
	 * @return 기다리지 않아도 되면 비어 있다
	 */
	public static Optional<Instant> retryAt(FailureRecord record, Instant now) {
		FailureRecord current = current(record, now);
		Duration wait = waitAfter(current.failures());
		if (wait.isZero()) {
			return Optional.empty();
		}
		Instant retryAt = Objects.requireNonNull(current.lastFailedAt()).plus(wait);
		return now.isBefore(retryAt) ? Optional.of(retryAt) : Optional.empty();
	}

	/** 한 번 더 실패한 기록 — 잊을 만큼 오래된 기록은 0 회에서 센다 */
	public static FailureRecord failed(FailureRecord record, Instant now) {
		return new FailureRecord(current(record, now).failures() + 1, now);
	}

	/** 지울 기록의 기준 시각 — 마지막 실패가 이보다 이르면 잊은 기록이다 */
	public static Instant forgetBefore(Instant now) {
		return now.minus(FORGET_AFTER);
	}

	/** 연속 실패 n 회 뒤의 대기 */
	static Duration waitAfter(int failures) {
		if (failures <= FREE_FAILURES) {
			return Duration.ZERO;
		}
		int doublings = Math.min(failures - FREE_FAILURES - 1, MAX_DOUBLINGS);
		Duration wait = FIRST_WAIT.multipliedBy(1L << doublings);
		return (wait.compareTo(MAX_WAIT) > 0) ? MAX_WAIT : wait;
	}

	private static FailureRecord current(FailureRecord record, Instant now) {
		Instant last = record.lastFailedAt();
		return (last == null || !now.isBefore(last.plus(FORGET_AFTER))) ? FailureRecord.NONE : record;
	}

}
