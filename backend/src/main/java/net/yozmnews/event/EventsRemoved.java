package net.yozmnews.event;

import java.util.HashSet;
import java.util.List;

/**
 * 사건을 지웠다 — 기사가 빠져 빈 사건과, 그 사건에 합쳐져 있어 함께 지운 사건 · docs/specs/event.md §3 · event-R25.
 * <p>
 * 사건을 가리키는 모듈이 받아 자기 행을 따라 지운다. 지운 사건 ID 는 다시 쓰이지 않는다.
 *
 * @param eventIds 지운 사건 — 비어 있지 않고 중복이 없다
 */
public record EventsRemoved(List<EventId> eventIds) {

	/**
	 * @throws IllegalArgumentException 목록이 비었거나 같은 사건이 두 번 있을 때
	 */
	public EventsRemoved {
		eventIds = List.copyOf(eventIds);
		if (eventIds.isEmpty()) {
			throw new IllegalArgumentException("지운 사건이 없다");
		}
		if (new HashSet<>(eventIds).size() != eventIds.size()) {
			throw new IllegalArgumentException("같은 사건이 두 번 있다");
		}
	}

}
