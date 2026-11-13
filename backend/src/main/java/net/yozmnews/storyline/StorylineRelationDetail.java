package net.yozmnews.storyline;

import java.util.Objects;

import net.yozmnews.evaluation.RelationJudgementId;

/**
 * 관계 하나와 그 판정 — docs/specs/storyline.md §3 · storyline-R34.
 *
 * @param probability 맺은 판정에서 가장 높았던 답의 확률
 * @param judgement 그 판정 원본 ID — 원본은 먼저 지워졌을 수 있다
 * @param criteria 그 판정에 쓴 관계 기준
 */
public record StorylineRelationDetail(StorylineRelationView relation, double probability, RelationJudgementId judgement,
		StorylineRelationCriteria criteria) {

	public StorylineRelationDetail {
		Objects.requireNonNull(relation, "relation");
		Objects.requireNonNull(judgement, "judgement");
		Objects.requireNonNull(criteria, "criteria");
	}

}
