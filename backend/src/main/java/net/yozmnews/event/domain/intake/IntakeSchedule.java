package net.yozmnews.event.domain.intake;

import java.time.Duration;

/**
 * 사건 묶기 대기의 상수 — 점유 기간 · 회차의 점유 상한의 정본 · docs/specs/event.md §4 · event-R6.
 * <p>
 * 값은 모두 측정 전 목표다.
 */
public final class IntakeSchedule {

	/** 회차가 대기 행을 점유하는 기간 — 만료되면 다른 회차가 다시 가져간다 */
	public static final Duration LEASE = Duration.ofMinutes(10);

	/** 회차 하나가 점유하는 대기 행 수의 상한 */
	public static final int BATCH_SIZE = 64;

	private IntakeSchedule() {
	}

}
