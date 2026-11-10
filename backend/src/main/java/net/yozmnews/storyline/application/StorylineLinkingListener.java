package net.yozmnews.storyline.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.Comparator;

import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventsChanged;
import net.yozmnews.storyline.domain.queue.LinkingQueueRepository;
import net.yozmnews.storyline.domain.queue.PendingEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * {@link EventsChanged} 리스너 — 바뀐 사건을 잇기 대기에 넣는다 · docs/specs/storyline.md §4 · storyline-R1 · storyline-R2.
 * <p>
 * 비동기 · 새 트랜잭션이고 다시 처리해도 다시 알림 수만 는다 — 판정은 회차가 지금 상태를 읽어 한다. 사건 ID 오름차순으로 넣어 여러 행의
 * 잠금 순서를 맞춘다.
 */
@Component
class StorylineLinkingListener {

	private final EventDirectory events;

	private final LinkingQueueRepository queue;

	private final InstantSource clock;

	@Autowired
	StorylineLinkingListener(EventDirectory events, LinkingQueueRepository queue) {
		this(events, queue, InstantSource.system());
	}

	StorylineLinkingListener(EventDirectory events, LinkingQueueRepository queue, InstantSource clock) {
		this.events = events;
		this.queue = queue;
		this.clock = clock;
	}

	@ApplicationModuleListener
	public void on(EventsChanged changed) {
		Instant now = this.clock.instant();
		changed.eventIds()
			.stream()
			.sorted(Comparator.comparingLong(EventId::value))
			.forEach(id -> this.queue.enqueue(id.value(), PendingEvent.orderAt(id, this.events.find(id)), now));
	}

}
