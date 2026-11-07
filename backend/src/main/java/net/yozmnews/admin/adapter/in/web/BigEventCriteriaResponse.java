package net.yozmnews.admin.adapter.in.web;

import java.math.BigDecimal;

import net.yozmnews.event.BigEventCriteria;

/**
 * 큰 사건 기준 — 서로 다른 소스 수의 하한과 중요도 평균의 하한 · docs/specs/admin.md §3 · event-R21.
 */
record BigEventCriteriaResponse(int minSources, BigDecimal minImportance) {

	static BigEventCriteriaResponse of(BigEventCriteria criteria) {
		return new BigEventCriteriaResponse(criteria.minSources(), criteria.minImportance());
	}

}
