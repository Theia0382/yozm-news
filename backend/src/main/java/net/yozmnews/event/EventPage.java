package net.yozmnews.event;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 사건 목록 한 쪽 · docs/specs/event.md §3.
 *
 * @param next 다음 쪽의 커서 — 더 없으면 null
 */
public record EventPage(List<EventSummary> events, @Nullable EventCursor next) {

	public EventPage {
		events = List.copyOf(events);
	}

}
