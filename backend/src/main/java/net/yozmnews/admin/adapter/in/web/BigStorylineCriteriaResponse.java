package net.yozmnews.admin.adapter.in.web;

import java.math.BigDecimal;

import net.yozmnews.storyline.BigStorylineCriteria;

/**
 * 큰 판정 기준 — 서로 다른 소스 수의 하한과 중요도 평균의 하한 · docs/specs/admin.md §3 · storyline-R20.
 */
record BigStorylineCriteriaResponse(int minSources, BigDecimal minImportance) {

	static BigStorylineCriteriaResponse of(BigStorylineCriteria criteria) {
		return new BigStorylineCriteriaResponse(criteria.minSources(), criteria.minImportance());
	}

}
