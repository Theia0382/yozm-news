package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;

import net.yozmnews.storyline.LinkScore;
import net.yozmnews.storyline.LinkedBy;
import net.yozmnews.storyline.StorylineMembershipView;
import org.jspecify.annotations.Nullable;

/**
 * 사건 하나의 소속 근거 — 그 사건을 직접 가진 스토리라인과 정한 것 · 점수 · 기준 · 판정 확률 · docs/specs/admin.md §5 admin-R7.
 *
 * @param storylineId 그 사건을 직접 가진 스토리라인 — 상세의 스토리라인이거나 그 하위
 * @param score 점수 — 병합 옮김이면 null(코사인 · 겹침 · 기준도 같다)
 * @param probability 띠 안에서 스토리라인 소속 판정으로 정했으면 그 확률 — 위 기준 이상이거나 병합 옮김이면 null
 * @param judgementId 그 판정의 원본 ID — 확률과 함께 있거나 함께 없다
 * @param mergedFrom 스토리라인 합치기로 옮겨 온 소속이면 그 사건을 직전에 가졌던 합쳐진 스토리라인 ID — 아니면 null
 */
record StorylineMembershipResponse(long storylineId, LinkedBy linkedBy, @Nullable Double score,
		@Nullable Double cosine, @Nullable Double keyTermOverlap, @Nullable LinkingCriteriaResponse criteria,
		@Nullable Double probability, @Nullable Long judgementId, Instant linkedAt, @Nullable Long mergedFrom) {

	static StorylineMembershipResponse of(StorylineMembershipView membership) {
		LinkScore score = membership.score();
		return new StorylineMembershipResponse(membership.storyline().value(), membership.linkedBy(),
				(score == null) ? null : score.score(), (score == null) ? null : score.cosine(),
				(score == null) ? null : score.keyTermOverlap(),
				(membership.criteria() == null) ? null : LinkingCriteriaResponse.of(membership.criteria()),
				membership.probability(), (membership.judgement() == null) ? null : membership.judgement().value(),
				membership.linkedAt(), (membership.mergedFrom() == null) ? null : membership.mergedFrom().value());
	}

}
