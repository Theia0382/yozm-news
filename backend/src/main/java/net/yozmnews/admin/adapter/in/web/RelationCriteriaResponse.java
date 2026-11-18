package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.storyline.StorylineRelationCriteria;

/**
 * 관계 기준 — 확률 기준 · 한 사안 기준 · 사건 수 하한 · 후보 점수 기준 · 시간 창(초) · 합치기 확률 기준 · docs/specs/admin.md §5
 * admin-R7.
 *
 * @param mergeProbability 같은 사안의 조각이라는 답의 확률이 이 이상이면 두 스토리라인을 합친다
 */
record RelationCriteriaResponse(double probability, double oneMatter, int minEvents, double candidateScore,
		long windowSeconds, double mergeProbability) {

	static RelationCriteriaResponse of(StorylineRelationCriteria criteria) {
		return new RelationCriteriaResponse(criteria.probability(), criteria.oneMatter(), criteria.minEvents(),
				criteria.candidateScore(), criteria.window().toSeconds(), criteria.mergeProbability());
	}

}
