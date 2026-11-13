package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;

import net.yozmnews.evaluation.RelationJudgementView;
import net.yozmnews.storyline.RelationOutcome;
import net.yozmnews.storyline.StorylineRelationAsked;
import org.jspecify.annotations.Nullable;

/**
 * 물은 쌍 하나 — 상대 · 마지막으로 물은 결말 · 작은 쪽 사건 수 · 기준 · 판정 원본 · docs/specs/admin.md §5 admin-R7.
 *
 * @param judgementId 판정 원본 ID — 판정 원본이 남지 않은 결말(물을 것 없음 · 입력 거부)이면 null
 * @param judgement 판정 원본 — 원본 ID 가 없거나 원본이 지워졌으면 null
 */
record StorylineAskedResponse(long otherId, int smallerEvents, RelationOutcome outcome, Instant askedAt,
		RelationCriteriaResponse criteria, @Nullable Long judgementId, @Nullable RelationJudgementResponse judgement) {

	static StorylineAskedResponse of(StorylineRelationAsked asked, @Nullable RelationJudgementView judgement) {
		return new StorylineAskedResponse(asked.other().value(), asked.smallerEvents(), asked.outcome(), asked.askedAt(),
				RelationCriteriaResponse.of(asked.criteria()),
				(asked.judgement() == null) ? null : asked.judgement().value(),
				(judgement == null) ? null : RelationJudgementResponse.of(judgement));
	}

}
