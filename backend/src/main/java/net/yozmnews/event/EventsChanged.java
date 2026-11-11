package net.yozmnews.event;

import java.util.HashSet;
import java.util.List;

/**
 * 사건이 바뀌었다 — 열기 · 붙이기 · 빼기 · 옮기기 · 합치기 · 닫기 · 지우기 · docs/specs/event.md §3 · event-R23.
 * <p>
 * 무엇이 일어났는지는 싣지 않는다 — 받는 쪽이 {@link EventDirectory#find} 로 지금 상태를 읽어 가른다.
 *
 * @param eventIds 바뀐 사건 — 비어 있지 않고 중복이 없다
 */
public record EventsChanged(List<EventId> eventIds) {

	/**
	 * @throws IllegalArgumentException 목록이 비었거나 같은 사건이 두 번 있을 때
	 */
	public EventsChanged {
		eventIds = List.copyOf(eventIds);
		if (eventIds.isEmpty()) {
			throw new IllegalArgumentException("바뀐 사건이 없다");
		}
		if (new HashSet<>(eventIds).size() != eventIds.size()) {
			throw new IllegalArgumentException("같은 사건이 두 번 있다");
		}
	}

}
