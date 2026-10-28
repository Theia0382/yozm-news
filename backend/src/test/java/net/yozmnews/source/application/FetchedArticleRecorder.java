package net.yozmnews.source.application;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.yozmnews.source.ArticleFetched;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 수집 테스트용 리스너 — 커밋된 {@link ArticleFetched} 와 그 리스너가 불린 스레드가 가상 스레드인지를 모은다. 커밋 뒤 발행한 스레드에서
 * 불리므로, 수집 작업의 스레드를 그대로 본다.
 */
@Component
public class FetchedArticleRecorder {

	private final List<Recorded> recorded = new CopyOnWriteArrayList<>();

	@TransactionalEventListener
	public void on(ArticleFetched event) {
		this.recorded.add(new Recorded(event, Thread.currentThread().isVirtual()));
	}

	List<Recorded> forFeed(long feedId) {
		return this.recorded.stream().filter(item -> item.event().feedId().value() == feedId).toList();
	}

	void clear() {
		this.recorded.clear();
	}

	record Recorded(ArticleFetched event, boolean virtualThread) {
	}

}
