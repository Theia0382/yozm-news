package net.yozmnews.storyline.domain.queue;

import java.time.Duration;

import net.yozmnews.evaluation.EvaluatorTiming;

/**
 * 잇기 대기의 상수 — 점유 기간 · 뭉치의 점유 상한 · 실패 상한의 정본 · docs/specs/storyline.md §5 · storyline-R3 · storyline-R4.
 * <p>
 * 값은 모두 측정 전 목표다. 평가기 일시 실패 뒤 미루는 간격은 {@link EvaluatorTiming#retryDelay} 가 정본이다.
 */
public final class LinkingSchedule {

	/** 뭉치 하나가 점유하는 대기 행 수의 상한 — 회차는 뭉치를 되풀이한다(docs/specs/platform.md 반복 회차) */
	public static final int BATCH_SIZE = 32;

	/** 실패 상한 — 판정이 예상 못한 예외로 이만큼 실패하면 대기에서 뺀다. 평가기 일시 실패는 세지 않는다 */
	public static final int MAX_FAILURES = 5;

	/**
	 * 사건 하나를 점유한 동안 판정을 시도하는 수의 상한 — 시도마다 스토리라인 소속 판정을 한 번까지 묻는다(붙이기 묻기 · 짝 묻기 · 쓰기). 남으면
	 * 점유를 풀어 다음 뭉치가 잇는다
	 */
	public static final int MAX_ATTEMPTS_PER_CLAIM = 3;

	/** 점유 기간에서 판정을 기다리는 시간 밖의 여유 — 판정 · 기록 트랜잭션 */
	private static final Duration LEASE_MARGIN = Duration.ofMinutes(2);

	/**
	 * 뭉치가 대기 행을 점유하는 기간 — 만료되면 다른 뭉치가 다시 가져간다. 뭉치 하나가 사건마다 시도 상한만큼 판정을 가장 길게 기다려도
	 * 안에 든다: {@link EvaluatorTiming#LONGEST_JUDGEMENT} × {@link #BATCH_SIZE} × {@link #MAX_ATTEMPTS_PER_CLAIM} + 여유
	 */
	public static final Duration LEASE = EvaluatorTiming.LONGEST_JUDGEMENT.multipliedBy((long) BATCH_SIZE * MAX_ATTEMPTS_PER_CLAIM)
		.plus(LEASE_MARGIN);

	private LinkingSchedule() {
	}

	/** 실패 수가 상한에 이르렀는가 — 실패 수 ≥ {@link #MAX_FAILURES} */
	public static boolean failuresExhausted(int failures) {
		return failures >= MAX_FAILURES;
	}

}
