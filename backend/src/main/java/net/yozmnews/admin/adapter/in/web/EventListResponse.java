package net.yozmnews.admin.adapter.in.web;

import java.util.List;

import net.yozmnews.event.EventPeriod;
import org.jspecify.annotations.Nullable;

/**
 * 사건 목록 한 쪽과 날짜 범위의 상한 — docs/specs/admin.md §3 · admin-R7. 상한은 {@link EventPeriod#MAX_DAYS} 에서 읽는다.
 *
 * @param next 다음 쪽 커서 — 더 없으면 null
 */
record EventListResponse(List<EventRowResponse> events, @Nullable EventCursorResponse next, int maxDays) {

	static EventListResponse of(List<EventRowResponse> events, @Nullable EventCursorResponse next) {
		return new EventListResponse(events, next, EventPeriod.MAX_DAYS);
	}

}
