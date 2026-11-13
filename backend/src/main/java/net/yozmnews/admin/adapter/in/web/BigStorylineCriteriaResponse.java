package net.yozmnews.admin.adapter.in.web;

import java.math.BigDecimal;

import net.yozmnews.storyline.BigStorylineCriteria;

/**
 * 큰 판정 기준 — 보도 강도의 하한 · docs/specs/admin.md §3 · storyline-R20.
 */
record BigStorylineCriteriaResponse(BigDecimal minIntensity) {

	static BigStorylineCriteriaResponse of(BigStorylineCriteria criteria) {
		return new BigStorylineCriteriaResponse(criteria.minIntensity());
	}

}
