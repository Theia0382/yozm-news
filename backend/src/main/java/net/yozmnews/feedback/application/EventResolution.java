package net.yozmnews.feedback.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import org.springframework.stereotype.Component;

/**
 * 기록한 사건 풀기 — 이 모듈의 정본. {@code event} 의 풀기 조회를 {@link EventDirectory#MAX_RESOLVE_EVENTS} 씩 나눠 부른다.
 * docs/specs/feedback.md §4 · feedback-R8
 * <p>
 * 기록은 누른 때의 사건 ID 로 남고 고쳐 쓰지 않는다 — 합쳐진 사건은 읽을 때마다 여기서 남는 사건으로 푼다.
 */
@Component
class EventResolution {

	private final EventDirectory events;

	EventResolution(EventDirectory events) {
		this.events = Objects.requireNonNull(events, "events");
	}

	/**
	 * 사건마다 지금 사건 — 합쳐진 사건이면 남는 사건, 아니면 그 사건.
	 * @return 사건 → 지금 사건 — 없는(지운) 사건은 빠진다
	 */
	Map<EventId, EventId> currentOf(Collection<EventId> recorded) {
		List<EventId> ids = recorded.stream().distinct().toList();
		Map<EventId, EventId> current = new HashMap<>();
		for (int i = 0; i < ids.size(); i += EventDirectory.MAX_RESOLVE_EVENTS) {
			int end = Math.min(ids.size(), i + EventDirectory.MAX_RESOLVE_EVENTS);
			current.putAll(this.events.currentOf(ids.subList(i, end)));
		}
		return current;
	}

	/**
	 * 지금 사건마다 그 사건으로 풀리는 기록한 사건 — 그 사건 자신과 그 사건으로 합쳐진 사건.
	 * @return 기록한 사건 → 지금 사건 — 주어진 사건은 모두 자기 자신으로 든다
	 */
	Map<EventId, EventId> recordedOf(Collection<EventId> currents) {
		List<EventId> ids = currents.stream().distinct().toList();
		Map<EventId, EventId> recorded = new HashMap<>();
		ids.forEach(id -> recorded.put(id, id));
		for (int i = 0; i < ids.size(); i += EventDirectory.MAX_RESOLVE_EVENTS) {
			int end = Math.min(ids.size(), i + EventDirectory.MAX_RESOLVE_EVENTS);
			this.events.absorbedInto(new ArrayList<>(ids.subList(i, end)))
				.forEach((survivor, absorbed) -> absorbed.forEach(merged -> recorded.put(merged, survivor)));
		}
		return recorded;
	}

}
