package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.storyline.StorylineLinkingCriteria;

/**
 * 잇기 기준 — 시간 창(초) · 기준 점수 · docs/specs/admin.md §5 admin-R7.
 */
record LinkingCriteriaResponse(long windowSeconds, double threshold) {

	static LinkingCriteriaResponse of(StorylineLinkingCriteria criteria) {
		// 기준 점수 칸은 위 기준이다 — 묻지 않고 붙이는 점수
		return new LinkingCriteriaResponse(criteria.window().toSeconds(), criteria.upper());
	}

}
