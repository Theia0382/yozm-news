package net.yozmnews.admin.adapter.in.web;

import java.util.List;

/**
 * 큰 사건 기준 미리보기 — 같은 기간을 지금 기준과 제안 기준으로 판정한 두 목록 · docs/specs/admin.md §3 · event-R21. 아무것도 쓰지 않는다.
 * 들어오고 빠지는 사건은 화면이 두 목록의 사건 ID 로 가린다.
 */
record BigEventPreviewResponse(BigEventCriteriaResponse current, BigEventCriteriaResponse proposed,
		List<BigEventRowResponse> currentEvents, List<BigEventRowResponse> proposedEvents) {
}
