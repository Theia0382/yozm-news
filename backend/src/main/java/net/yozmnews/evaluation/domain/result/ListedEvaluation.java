package net.yozmnews.evaluation.domain.result;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.evaluation.EvaluationId;

/**
 * 목록 · 표본이 읽은 평가 하나 — 평가 ID · 평가 시각과 저장된 평가(대표 형식의 확률 · 판정 원본 ID 포함) · docs/specs/tag.md §4.
 */
public record ListedEvaluation(EvaluationId id, Instant evaluatedAt, SampleRow sample) {

	public ListedEvaluation {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(evaluatedAt, "evaluatedAt");
		Objects.requireNonNull(sample, "sample");
	}

}
