package net.yozmnews.storyline.domain.relation;

import java.time.Duration;

import net.yozmnews.evaluation.EvaluatorTiming;

/**
 * 관계 대기의 상수 — 점유 기간 · 회차의 점유 상한 · 스토리라인마다 쌍 수의 정본 · docs/specs/storyline.md §5 · storyline-R31.
 * <p>
 * 값은 모두 측정 전 목표다. 평가기 일시 실패 뒤 미루는 간격은 {@link EvaluatorTiming#retryDelay} 가 정본이다.
 */
public final class RelationSchedule {

	/** 회차 하나가 점유하는 대기 행 수의 상한 */
	public static final int BATCH_SIZE = 4;

	/** 회차 하나가 스토리라인 하나에서 묻는 쌍 수의 상한 — 쌍마다 관계 판정 한 번 */
	public static final int MAX_PAIRS = 5;

	/** 점유 기간에서 판정을 기다리는 시간 밖의 여유 — 후보 고르기 · 반영 트랜잭션 */
	private static final Duration LEASE_MARGIN = Duration.ofMinutes(10);

	/**
	 * 회차가 대기 행을 점유하는 기간 — 만료되면 다른 회차가 다시 가져간다. 회차 한 묶음이 스토리라인마다 쌍 상한만큼 판정을 가장 길게
	 * 기다려도 안에 든다: {@link EvaluatorTiming#LONGEST_JUDGEMENT} × {@link #BATCH_SIZE} × {@link #MAX_PAIRS} + 여유
	 */
	public static final Duration LEASE = EvaluatorTiming.LONGEST_JUDGEMENT.multipliedBy((long) BATCH_SIZE * MAX_PAIRS)
		.plus(LEASE_MARGIN);

	private RelationSchedule() {
	}

}
