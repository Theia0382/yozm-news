package net.yozmnews.evaluation.domain.result;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.evaluation.EvaluationId;
import net.yozmnews.evaluation.domain.overview.EvaluationSample;

/**
 * 목록 · 표본이 읽은 평가 하나 — 평가 ID · 평가 시각과 저장된 평가(대표 형식의 확률 · 입력 토큰 포함) · docs/specs/evaluation.md §4.
 */
public record ListedEvaluation(EvaluationId id, Instant evaluatedAt, EvaluationSample sample) {

	public ListedEvaluation {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(evaluatedAt, "evaluatedAt");
		Objects.requireNonNull(sample, "sample");
	}

}
