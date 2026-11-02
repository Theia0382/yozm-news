package net.yozmnews.evaluation.domain.queue;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 평가 대기의 일정 — 정착 시간 · 점유 기간 · 재시도 간격과 상한의 정본 · docs/specs/evaluation.md §5.
 * <p>
 * 값은 모두 측정 전 목표다.
 */
public final class EvaluationSchedule {

	/** 대기에 넣은 뒤 평가할 수 있기까지 — 같은 기사의 다른 피드 엔트리가 붙을 시간 */
	public static final Duration SETTLE_DELAY = Duration.ofMinutes(30);

	/** 회차가 대기 행을 점유하는 기간 — 만료되면 다른 회차가 다시 가져간다 */
	public static final Duration LEASE = Duration.ofMinutes(30);

	/** 일시 실패의 시도 상한 — 이 번째 일시 실패에서 실패로 남긴다 */
	public static final int MAX_ATTEMPTS = 5;

	/** 첫 일시 실패 뒤의 재시도 간격 — 실패마다 두 배 */
	public static final Duration FIRST_RETRY = Duration.ofMinutes(1);

	/** 재시도 간격의 상한 */
	public static final Duration MAX_RETRY = Duration.ofHours(1);

	private EvaluationSchedule() {
	}

	/** 대기에 넣은 기사를 평가할 수 있는 첫 시각 */
	public static Instant eligibleAfterEnqueue(Instant enqueuedAt) {
		return Objects.requireNonNull(enqueuedAt, "enqueuedAt").plus(SETTLE_DELAY);
	}

	/**
	 * 재평가로 넣거나 되돌린 기사를 평가할 수 있는 첫 시각 — 넣은 그 시각이다. 이미 엔트리가 붙은 기사라 정착 시간을 두지 않는다
	 * (evaluation-R18 · R25).
	 */
	public static Instant eligibleAfterRequeue(Instant requeuedAt) {
		return Objects.requireNonNull(requeuedAt, "requeuedAt");
	}

	/**
	 * 일시 실패 뒤 다시 시도하기까지의 간격 — {@code min(FIRST_RETRY × 2^(attempts − 1), MAX_RETRY)}. 상한에 이르면 더 곱하지 않아 넘치지
	 * 않는다.
	 * @param attempts 지금까지의 일시 실패 수 — 1 이상
	 * @throws IllegalArgumentException 시도 수가 1 미만일 때
	 */
	public static Duration retryDelay(int attempts) {
		if (attempts < 1) {
			throw new IllegalArgumentException("시도 수는 1 이상이다: " + attempts);
		}
		Duration delay = FIRST_RETRY;
		for (int i = 1; i < attempts && delay.compareTo(MAX_RETRY) < 0; i++) {
			delay = delay.multipliedBy(2);
		}
		return (delay.compareTo(MAX_RETRY) < 0) ? delay : MAX_RETRY;
	}

}
