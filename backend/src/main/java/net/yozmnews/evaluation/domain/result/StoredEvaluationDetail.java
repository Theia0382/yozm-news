package net.yozmnews.evaluation.domain.result;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.evaluation.EvaluationId;

/**
 * 상세가 읽은 평가 하나 — 평가기 · 모델 버전 · 입력 토큰 · 분포와 저장된 평가 · docs/specs/evaluation.md §4.
 *
 * @param formatDistribution 형식 선택지 → 확률 — 평가기가 준 분포 그대로
 * @param importanceDistribution 중요도 수준 → 확률
 */
public record StoredEvaluationDetail(EvaluationId id, Instant evaluatedAt, String evaluator, String modelVersion,
		int inputTokens, Map<String, Double> formatDistribution, Map<Integer, Double> importanceDistribution,
		StoredEvaluation evaluation) {

	public StoredEvaluationDetail {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(evaluatedAt, "evaluatedAt");
		Objects.requireNonNull(evaluator, "evaluator");
		Objects.requireNonNull(modelVersion, "modelVersion");
		formatDistribution = Map.copyOf(formatDistribution);
		importanceDistribution = Map.copyOf(importanceDistribution);
		Objects.requireNonNull(evaluation, "evaluation");
	}

}
