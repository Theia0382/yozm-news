package net.yozmnews.event.application;

import java.util.List;
import java.util.Optional;

import net.yozmnews.event.domain.event.EventArticleRepository;
import net.yozmnews.event.domain.event.EventRepository;
import net.yozmnews.event.domain.event.EventShrink;
import net.yozmnews.event.domain.event.LockedEvent;
import net.yozmnews.event.domain.event.PublishedSpan;
import org.springframework.stereotype.Component;

/**
 * 기사를 뺀 사건을 쓴다 — 지운 기사 따르기와 재판정의 옮기기 · 떼어 내기가 함께 쓰는 정본 · docs/specs/event.md §4.
 * <p>
 * 뺀 기사의 배정이 이미 지워지거나 옮겨진 뒤, 잠근 사건에 대해 같은 트랜잭션에서 부른다. 비면 사건을 지우고(그 사건을 가리키는 후보와
 * 함께), 아니면 새 중심 · 수 · 핵심 말과 남은 배정의 발행 시각 범위를 쓴다. 남은 수가 있는데 배정이 없으면 남은 배정이 정본이라 지운다.
 */
@Component
class EventShrinkWriter {

	private final EventRepository events;

	private final EventArticleRepository assignments;

	EventShrinkWriter(EventRepository events, EventArticleRepository assignments) {
		this.events = events;
		this.assignments = assignments;
	}

	void write(LockedEvent locked, EventShrink shrink) {
		switch (shrink) {
			case EventShrink.Emptied emptied -> this.events.delete(locked.id());
			case EventShrink.Shrunk shrunk -> {
				Optional<PublishedSpan> span = this.assignments.publishedSpan(locked.id());
				if (span.isEmpty()) {
					this.events.delete(locked.id());
					return;
				}
				this.events.update(new LockedEvent(locked.id(), locked.status(), locked.modelVersion(), shrunk.centroid(),
						shrunk.articleCount(), span.get().first(), span.get().last()), List.of(), shrunk.removedTerms());
			}
		}
	}

}
