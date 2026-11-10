package net.yozmnews.storyline.domain.queue;

import java.time.Duration;

/**
 * 잇기 대기의 상수 — 점유 기간 · 회차의 점유 상한 · 실패 상한의 정본 · docs/specs/storyline.md §5 · storyline-R3 · storyline-R4.
 * <p>
 * 값은 모두 측정 전 목표다.
 */
public final class LinkingSchedule {

	/** 회차가 대기 행을 점유하는 기간 — 만료되면 다른 회차가 다시 가져간다 */
	public static final Duration LEASE = Duration.ofMinutes(10);

	/** 회차 하나가 점유하는 대기 행 수의 상한 */
	public static final int BATCH_SIZE = 64;

	/** 실패 상한 — 판정이 이만큼 실패하면 대기에서 뺀다 */
	public static final int MAX_FAILURES = 3;

	private LinkingSchedule() {
	}

	/** 실패 수가 상한에 이르렀는가 — 실패 수 ≥ {@link #MAX_FAILURES} */
	public static boolean failuresExhausted(int failures) {
		return failures >= MAX_FAILURES;
	}

}
