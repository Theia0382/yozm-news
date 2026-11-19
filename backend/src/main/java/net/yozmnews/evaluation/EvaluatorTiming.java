package net.yozmnews.evaluation;

import java.time.Duration;

/**
 * 평가기 시간 — 평가기 요청 하나의 마감, 판정 한 번이 평가기를 기다리는 가장 긴 시간, 일시 실패 뒤 다시 묻기까지의 간격의 정본 ·
 * docs/specs/evaluation.md §3 · evaluation-R53.
 * <p>
 * 판정을 부르는 회차는 점유 기간을 {@link #LONGEST_JUDGEMENT} 에서 계산하고, 판정이 일시 실패({@link EvaluatorFailureKind#TRANSIENT})로
 * 끝나면 {@link #retryDelay} 뒤에 다시 묻는다. 값은 모두 측정 전 목표다.
 */
public final class EvaluatorTiming {

	/** 평가기 요청 하나(헤더 · 본문 전부)의 마감 */
	public static final Duration REQUEST_DEADLINE = Duration.ofSeconds(15);

	/** 판정 한 번이 보내는 요청 수의 상한 — 본 요청과, 문맥 초과일 때 같은 질문을 빈 state 로 다시 묻는 확인 요청 */
	private static final int REQUESTS_PER_JUDGEMENT = 2;

	/**
	 * 판정 공개 API 한 번이 평가기를 기다리는 가장 긴 시간 — 요청 수의 상한 × {@link #REQUEST_DEADLINE}. 평가기는 요청을 다시 보내지
	 * 않는다
	 */
	public static final Duration LONGEST_JUDGEMENT = REQUEST_DEADLINE.multipliedBy(REQUESTS_PER_JUDGEMENT);

	/** 첫 일시 실패 뒤의 간격 — 일시 실패마다 두 배 */
	public static final Duration FIRST_RETRY = Duration.ofMinutes(1);

	/** 간격의 상한 — 일시 실패가 이어지는 동안 이 간격으로 다시 묻는다 */
	public static final Duration MAX_RETRY = Duration.ofHours(1);

	private EvaluatorTiming() {
	}

	/**
	 * 일시 실패 뒤 다시 묻기까지의 간격 — {@code min(FIRST_RETRY × 2^(transientFailures − 1), MAX_RETRY)}. 상한에 이르면 더 곱하지 않아
	 * 일시 실패 수가 커도 넘치지 않는다.
	 * @param transientFailures 이번 실패를 센 뒤의 일시 실패 수 — 1 이상
	 * @throws IllegalArgumentException 일시 실패 수가 1 미만일 때
	 */
	public static Duration retryDelay(int transientFailures) {
		if (transientFailures < 1) {
			throw new IllegalArgumentException("일시 실패 수는 1 이상이다: " + transientFailures);
		}
		Duration delay = FIRST_RETRY;
		for (int i = 1; i < transientFailures && delay.compareTo(MAX_RETRY) < 0; i++) {
			delay = delay.multipliedBy(2);
		}
		return (delay.compareTo(MAX_RETRY) < 0) ? delay : MAX_RETRY;
	}

}
