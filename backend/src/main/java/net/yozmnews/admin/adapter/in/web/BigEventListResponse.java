package net.yozmnews.admin.adapter.in.web;

import java.util.List;

import net.yozmnews.event.EventPeriod;

/**
 * 기간 안의 큰 사건과 그 판정에 쓴 지금 기준, 날짜 범위의 상한 — docs/specs/admin.md §3 · admin-R7. 상한은 {@link EventPeriod#MAX_DAYS}
 * 에서 읽는다.
 *
 * @param events 소스 수 · 중요도 평균 내림차순, 사건 ID 순
 */
record BigEventListResponse(BigEventCriteriaResponse criteria, List<BigEventRowResponse> events, int maxDays) {

	static BigEventListResponse of(BigEventCriteriaResponse criteria, List<BigEventRowResponse> events) {
		return new BigEventListResponse(criteria, events, EventPeriod.MAX_DAYS);
	}

}
