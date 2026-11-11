package net.yozmnews.evaluation.domain.result;

import java.util.Objects;

import net.yozmnews.evaluation.TagJudgementId;

/**
 * 흘려 받은 평가 한 건 — 저장된 평가 · 대표 형식의 확률 · 판정 원본 ID · docs/specs/tag.md §4.
 * <p>
 * 입력 토큰은 판정 원본({@code evaluation})에 있다 — 집계가 필요하면 판정 원본 ID 로 붙인다.
 *
 * @param formatProbability 대표 형식의 확률
 * @param judgementId 평가가 가리키는 태그 판정 원본
 */
public record SampleRow(StoredEvaluation evaluation, double formatProbability, TagJudgementId judgementId) {

	public SampleRow {
		Objects.requireNonNull(evaluation, "evaluation");
		Objects.requireNonNull(judgementId, "judgementId");
	}

}
