package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.storyline.StorylineLinkingCriteria;

/**
 * 잇기 기준 — 시간 창(초) · 기준 점수 · docs/specs/admin.md §5 admin-R7.
 */
record LinkingCriteriaResponse(long windowSeconds, double threshold) {

	static LinkingCriteriaResponse of(StorylineLinkingCriteria criteria) {
		return new LinkingCriteriaResponse(criteria.window().toSeconds(), criteria.threshold());
	}

}
