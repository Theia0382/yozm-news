package net.yozmnews.article.application;

import net.yozmnews.source.FeedDeleted;
import net.yozmnews.source.SourceDeleted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * {@link SourceDeleted} · {@link FeedDeleted} 리스너 — docs/specs/article.md §4 · article-R15
 */
@Component
class SourceRemovalListener {

	private final ArticleRemoval removal;

	SourceRemovalListener(ArticleRemoval removal) {
		this.removal = removal;
	}

	@ApplicationModuleListener
	public void on(SourceDeleted event) {
		this.removal.removeSource(event.sourceId());
	}

	@ApplicationModuleListener
	public void on(FeedDeleted event) {
		this.removal.removeFeed(event.sourceId(), event.feedId());
	}

}
