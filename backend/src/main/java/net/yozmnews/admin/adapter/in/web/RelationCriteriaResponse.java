package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.storyline.StorylineRelationCriteria;

/**
 * 관계 기준 — 확률 기준 · 한 사안 기준 · 사건 수 하한 · 후보 점수 기준 · 시간 창(초) · docs/specs/admin.md §5 admin-R7.
 */
record RelationCriteriaResponse(double probability, double oneMatter, int minEvents, double candidateScore,
		long windowSeconds) {

	static RelationCriteriaResponse of(StorylineRelationCriteria criteria) {
		return new RelationCriteriaResponse(criteria.probability(), criteria.oneMatter(), criteria.minEvents(),
				criteria.candidateScore(), criteria.window().toSeconds());
	}

}
