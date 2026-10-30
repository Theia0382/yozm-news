package net.yozmnews.article.application;

import net.yozmnews.source.ArticleFetched;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * {@link ArticleFetched} 리스너 — docs/specs/article.md §4 · article-R3 ~ article-R11
 */
@Component
class ArticleFetchedListener {

	private final ArticleIntake intake;

	ArticleFetchedListener(ArticleIntake intake) {
		this.intake = intake;
	}

	@ApplicationModuleListener
	public void on(ArticleFetched event) {
		this.intake.accept(event);
	}

}
