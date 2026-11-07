package net.yozmnews.event.domain.intake;

import java.time.Duration;

/**
 * 사건 묶기 대기의 상수 — 점유 기간 · 회차의 점유 상한 · 거부 상한의 정본 · docs/specs/event.md §4 · event-R6.
 * <p>
 * 값은 모두 측정 전 목표다.
 */
public final class IntakeSchedule {

	/** 회차가 대기 행을 점유하는 기간 — 만료되면 다른 회차가 다시 가져간다 */
	public static final Duration LEASE = Duration.ofMinutes(10);

	/** 회차 하나가 점유하는 대기 행 수의 상한 */
	public static final int BATCH_SIZE = 64;

	/** 거부 상한 — 임베더가 같은 기사를 이만큼 거부하면 대기에서 뺀다 */
	public static final int MAX_REJECTIONS = 3;

	private IntakeSchedule() {
	}

	/** 거부 수가 상한에 이르렀는가 — 거부 수 ≥ {@link #MAX_REJECTIONS} */
	public static boolean rejectionsExhausted(int rejections) {
		return rejections >= MAX_REJECTIONS;
	}

}
