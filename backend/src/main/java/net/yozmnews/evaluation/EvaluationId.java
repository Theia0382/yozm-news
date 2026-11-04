package net.yozmnews.evaluation;

/**
 * 평가 하나의 ID — {@code article_evaluations.id} · docs/specs/evaluation.md §3.
 */
public record EvaluationId(long value) {

	/**
	 * @throws IllegalArgumentException 1 미만일 때
	 */
	public EvaluationId {
		if (value < 1) {
			throw new IllegalArgumentException("평가 ID 는 1 이상이다: " + value);
		}
	}

}
