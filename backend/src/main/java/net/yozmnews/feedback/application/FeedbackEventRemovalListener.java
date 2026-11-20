package net.yozmnews.feedback.application;

import net.yozmnews.event.EventsRemoved;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * {@link EventsRemoved} 리스너 — docs/specs/feedback.md §4 · feedback-R5
 * <p>
 * 비동기 · 새 트랜잭션이고 다시 처리해도 결과가 같다. 지운 사건 ID 의 순서에 기대지 않는다.
 */
@Component
class FeedbackEventRemovalListener {

	private final FeedbackRemoval removal;

	FeedbackEventRemovalListener(FeedbackRemoval removal) {
		this.removal = removal;
	}

	@ApplicationModuleListener
	public void on(EventsRemoved event) {
		this.removal.apply(event.eventIds());
	}

}
