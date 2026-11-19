package net.yozmnews.storyline.domain.queue;

import java.time.Duration;

/**
 * 잇기 대기의 상수 — 점유 기간 · 회차의 점유 상한 · 실패 상한의 정본 · docs/specs/storyline.md §5 · storyline-R3 · storyline-R4.
 * <p>
 * 값은 모두 측정 전 목표다. 평가기 일시 실패 뒤 미루는 간격은 {@code evaluation} 의 {@code EvaluatorTiming#retryDelay} 가 정본이다.
 */
public final class LinkingSchedule {

	/**
	 * 회차가 대기 행을 점유하는 기간 — 만료되면 다른 회차가 다시 가져간다. 묶음 × 사건마다 요청 넷(붙이기 · 짝 묻기마다 확인 요청
	 * 포함 둘) × Jev 요청 마감 15초 = 32분보다 길다
	 */
	public static final Duration LEASE = Duration.ofMinutes(40);

	/** 회차 하나가 점유하는 대기 행 수의 상한 */
	public static final int BATCH_SIZE = 32;

	/** 실패 상한 — 판정이 예상 못한 예외로 이만큼 실패하면 대기에서 뺀다. 평가기 일시 실패는 세지 않는다 */
	public static final int MAX_FAILURES = 5;

	/** 사건 하나를 점유한 동안 판정을 시도하는 수의 상한 — 붙이기 묻기 · 짝 묻기 · 쓰기. 남으면 점유를 풀어 다음 회차가 잇는다 */
	public static final int MAX_ATTEMPTS_PER_CLAIM = 3;

	private LinkingSchedule() {
	}

	/** 실패 수가 상한에 이르렀는가 — 실패 수 ≥ {@link #MAX_FAILURES} */
	public static boolean failuresExhausted(int failures) {
		return failures >= MAX_FAILURES;
	}

}
