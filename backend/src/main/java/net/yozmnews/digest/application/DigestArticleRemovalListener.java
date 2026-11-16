package net.yozmnews.digest.application;

import net.yozmnews.article.ArticlesRemoved;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * {@link ArticlesRemoved} 리스너 — docs/specs/digest.md §4 · digest-R10
 * <p>
 * 비동기 · 새 트랜잭션이고 다시 처리해도 결과가 같다.
 */
@Component
class DigestArticleRemovalListener {

	private final DigestArticleRemoval removal;

	DigestArticleRemovalListener(DigestArticleRemoval removal) {
		this.removal = removal;
	}

	@ApplicationModuleListener
	public void on(ArticlesRemoved event) {
		this.removal.apply(event.articleIds());
	}

}
