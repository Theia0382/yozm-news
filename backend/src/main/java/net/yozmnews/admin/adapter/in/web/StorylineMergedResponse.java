package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;

import net.yozmnews.evaluation.RelationJudgementView;
import net.yozmnews.storyline.MergedStoryline;
import org.jspecify.annotations.Nullable;

/**
 * 이 스토리라인에 합쳐진 스토리라인 하나 — docs/specs/admin.md §5 admin-R7.
 *
 * @param id 합쳐진 스토리라인 ID
 * @param mergedAt 합친 시각
 * @param criteria 같은 사안의 조각으로 합쳤으면 그때의 관계 기준 — 정돈이 합쳤으면 null
 * @param judgementId 그 합치기를 정한 관계 판정 원본 ID — 기준과 함께 있거나 함께 없다
 * @param judgement 판정 원본 — 정돈이 합쳤거나 원본이 지워졌으면 null
 */
record StorylineMergedResponse(long id, Instant mergedAt, @Nullable RelationCriteriaResponse criteria,
		@Nullable Long judgementId, @Nullable RelationJudgementResponse judgement) {

	static StorylineMergedResponse of(MergedStoryline merged, @Nullable RelationJudgementView judgement) {
		return new StorylineMergedResponse(merged.id().value(), merged.mergedAt(),
				(merged.criteria() == null) ? null : RelationCriteriaResponse.of(merged.criteria()),
				(merged.judgement() == null) ? null : merged.judgement().value(),
				(judgement == null) ? null : RelationJudgementResponse.of(judgement));
	}

}
