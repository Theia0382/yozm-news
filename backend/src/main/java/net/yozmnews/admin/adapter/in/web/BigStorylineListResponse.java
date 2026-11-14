package net.yozmnews.admin.adapter.in.web;

import java.util.List;

import net.yozmnews.event.EventPeriod;

/**
 * 기간의 큰 스토리라인과 그 판정에 쓴 지금 기준, 그 기간의 소속 없는 사건, 날짜 범위의 상한 — docs/specs/admin.md §3 · admin-R7. 상한은
 * {@link EventPeriod#MAX_DAYS} 에서 읽는다.
 *
 * @param storylines 보도 강도 내림차순, 스토리라인 ID 순
 * @param unlinked 기간 안 기사가 있는데 어느 스토리라인에도 들지 않은 사건 — 큰 판정에 들지 않는 몫
 */
record BigStorylineListResponse(BigStorylineCriteriaResponse criteria, List<BigStorylineRowResponse> storylines,
		UnlinkedEventsResponse unlinked, int maxDays) {

	static BigStorylineListResponse of(BigStorylineCriteriaResponse criteria, List<BigStorylineRowResponse> storylines,
			UnlinkedEventsResponse unlinked) {
		return new BigStorylineListResponse(criteria, storylines, unlinked, EventPeriod.MAX_DAYS);
	}

}
