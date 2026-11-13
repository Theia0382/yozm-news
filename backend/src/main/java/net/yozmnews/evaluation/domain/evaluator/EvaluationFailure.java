package net.yozmnews.evaluation.domain.evaluator;

import java.util.Objects;

import net.yozmnews.evaluation.EvaluatorFailureKind;

/**
 * 예상한 이유로 평가 · 사건 소속 판정 · 관계 판정을 하지 못했다 — docs/specs/evaluation.md §4.
 *
 * @param kind 실패 갈래
 * @param message 운영자용 문구 — 비밀 값을 담지 않는다
 */
public record EvaluationFailure(EvaluatorFailureKind kind, String message)
		implements EvaluationResult, MembershipResult, RelationResult {

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

}
