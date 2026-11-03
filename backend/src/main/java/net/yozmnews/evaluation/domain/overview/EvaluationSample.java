package net.yozmnews.evaluation.domain.overview;

import java.util.Objects;

import net.yozmnews.evaluation.domain.result.StoredEvaluation;

/**
 * 평가 현황 집계의 입력 한 건 — 저장된 평가와 대표 형식의 확률 · 입력 토큰 · docs/specs/evaluation.md §4
 *
 * @param formatProbability 대표 형식의 확률
 * @param inputTokens 평가 요청의 입력 토큰 — 음수가 아니다
 */
public record EvaluationSample(StoredEvaluation evaluation, double formatProbability, int inputTokens) {

	/**
	 * @throws IllegalArgumentException 입력 토큰이 음수일 때
	 */
	public EvaluationSample {
		Objects.requireNonNull(evaluation, "evaluation");
		if (inputTokens < 0) {
			throw new IllegalArgumentException("입력 토큰은 음수가 아니다: " + inputTokens);
		}
	}

}
