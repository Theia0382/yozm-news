package net.yozmnews.evaluation.domain.evaluator;

import java.util.Objects;

/**
 * 예상한 이유로 평가하지 못했다 — docs/specs/evaluation.md §4.
 *
 * @param message 운영자용 문구 — 비밀 값을 담지 않는다
 */
public record EvaluationFailure(Kind kind, String message) implements EvaluationResult {

	/**
	 * @throws IllegalArgumentException 문구가 공백뿐일 때
	 */
	public EvaluationFailure {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(message, "message");
		if (message.isBlank()) {
			throw new IllegalArgumentException("실패 문구가 비었다");
		}
	}

	/**
	 * 실패 갈래 — 호출자가 재시도를 정하는 기준 · docs/specs/evaluation.md §4
	 */
	public enum Kind {

		/** 시간이 지나면 풀릴 수 있다 — 그 기사를 재시도한다 */
		TRANSIENT,
		/** 그 기사 때문이라 다시 보내도 같다 — 그 기사를 실패로 남긴다 */
		ARTICLE_REJECTED,
		/** 평가기 자체를 쓸 수 없다 — 기사 재시도를 쓰지 않고 회차를 멈춘다 */
		EVALUATOR_UNAVAILABLE

	}

}
