package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;

import net.yozmnews.evaluation.RelationJudgementView;
import net.yozmnews.storyline.StorylineRelationDetail;
import net.yozmnews.storyline.StorylineRelationKind;
import org.jspecify.annotations.Nullable;

/**
 * 스토리라인의 관계 하나 — 종류 · 앞 · 뒤 · 맺은 확률 · 기준 · 판정 원본 · docs/specs/admin.md §5 admin-R7.
 *
 * @param probability 맺은 판정에서 가장 높았던 답의 확률
 * @param judgementId 그 판정 원본 ID
 * @param judgement 판정 원본 — 원본이 지워졌으면 null
 */
record StorylineRelationResponse(StorylineRelationKind kind, long sourceId, long targetId, double probability,
		Instant linkedAt, RelationCriteriaResponse criteria, long judgementId,
		@Nullable RelationJudgementResponse judgement) {

	static StorylineRelationResponse of(StorylineRelationDetail detail, @Nullable RelationJudgementView judgement) {
		return new StorylineRelationResponse(detail.relation().kind(), detail.relation().source().value(),
				detail.relation().target().value(), detail.probability(), detail.relation().linkedAt(),
				RelationCriteriaResponse.of(detail.criteria()), detail.judgement().value(),
				(judgement == null) ? null : RelationJudgementResponse.of(judgement));
	}

}
