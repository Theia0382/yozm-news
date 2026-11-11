package net.yozmnews.evaluation.domain.result;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.evaluation.EvaluationId;
import net.yozmnews.evaluation.TagJudgementId;

/**
 * 상세가 읽은 평가 하나 — 판정 원본 ID · 분포와 저장된 평가 · docs/specs/tag.md §4. 평가기 · 모델 버전 · 입력 토큰은 판정 원본에서 붙인다.
 *
 * @param formatDistribution 형식 선택지 → 확률 — 평가기가 준 분포 그대로
 * @param importanceDistribution 중요도 수준 → 확률
 */
public record StoredEvaluationDetail(EvaluationId id, Instant evaluatedAt, TagJudgementId judgementId,
		Map<String, Double> formatDistribution, Map<Integer, Double> importanceDistribution,
		StoredEvaluation evaluation) {

	public StoredEvaluationDetail {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(evaluatedAt, "evaluatedAt");
		Objects.requireNonNull(judgementId, "judgementId");
		formatDistribution = Map.copyOf(formatDistribution);
		importanceDistribution = Map.copyOf(importanceDistribution);
		Objects.requireNonNull(evaluation, "evaluation");
	}

}
