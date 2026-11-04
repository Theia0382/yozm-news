package net.yozmnews.evaluation;

import java.time.Instant;
import java.util.Objects;

/**
 * 평가 목록에서 이어 받을 자리 — 마지막으로 받은 평가의 평가 시각과 ID · docs/specs/evaluation.md §3 · evaluation-R31.
 */
public record EvaluationCursor(Instant evaluatedAt, EvaluationId id) {

	public EvaluationCursor {
		Objects.requireNonNull(evaluatedAt, "evaluatedAt");
		Objects.requireNonNull(id, "id");
	}

}
