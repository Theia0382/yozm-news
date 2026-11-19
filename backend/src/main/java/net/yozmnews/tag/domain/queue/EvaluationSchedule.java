package net.yozmnews.tag.domain.queue;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import net.yozmnews.evaluation.EvaluatorTiming;

/**
 * 평가 대기의 일정 — 정착 시간 · 회차의 점유 상한 · 점유 기간의 정본 · docs/specs/tag.md §5.
 * <p>
 * 값은 모두 측정 전 목표다. 일시 실패 뒤 다시 평가하기까지의 간격은 {@link EvaluatorTiming#retryDelay} 가 정본이다.
 */
public final class EvaluationSchedule {

	/** 대기에 넣은 뒤 평가할 수 있기까지 — 같은 기사의 다른 피드 엔트리가 붙을 시간 */
	public static final Duration SETTLE_DELAY = Duration.ofMinutes(30);

	/** 회차 하나가 점유하는 대기 행 수의 상한 — 기사마다 태그 판정 한 번 */
	public static final int BATCH_SIZE = 50;

	/** 점유 기간에서 판정을 기다리는 시간 밖의 여유 — 대기 행 · 결과 쓰기 */
	private static final Duration LEASE_MARGIN = Duration.ofMinutes(5);

	/**
	 * 회차가 대기 행을 점유하는 기간 — 만료되면 다른 회차가 다시 가져간다. 회차 한 묶음이 기사마다 판정 한 번을 가장 길게 기다려도 안에
	 * 든다: {@link EvaluatorTiming#LONGEST_JUDGEMENT} × {@link #BATCH_SIZE} + 여유
	 */
	public static final Duration LEASE = EvaluatorTiming.LONGEST_JUDGEMENT.multipliedBy(BATCH_SIZE).plus(LEASE_MARGIN);

	private EvaluationSchedule() {
	}

	/** 대기에 넣은 기사를 평가할 수 있는 첫 시각 */
	public static Instant eligibleAfterEnqueue(Instant enqueuedAt) {
		return Objects.requireNonNull(enqueuedAt, "enqueuedAt").plus(SETTLE_DELAY);
	}

	/**
	 * 재평가로 넣거나 되돌린 기사를 평가할 수 있는 첫 시각 — 넣은 그 시각이다. 이미 엔트리가 붙은 기사라 정착 시간을 두지 않는다
	 * (tag-R18 · R25).
	 */
	public static Instant eligibleAfterRequeue(Instant requeuedAt) {
		return Objects.requireNonNull(requeuedAt, "requeuedAt");
	}

}
