package net.yozmnews.feedback.application;

import net.yozmnews.article.ArticlesRemoved;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * {@link ArticlesRemoved} 리스너 — docs/specs/feedback.md §4 · feedback-R5
 * <p>
 * 비동기 · 새 트랜잭션이고 다시 처리해도 결과가 같다.
 */
@Component
class FeedbackArticleRemovalListener {

	private final FeedbackRemoval removal;

	FeedbackArticleRemovalListener(FeedbackRemoval removal) {
		this.removal = removal;
	}

	@ApplicationModuleListener
	public void on(ArticlesRemoved event) {
		this.removal.apply(event.articleIds());
	}

}
