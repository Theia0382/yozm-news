package net.yozmnews.user.application;

import net.yozmnews.article.ArticlesRemoved;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * {@link ArticlesRemoved} 리스너 — 지운 기사를 넣은 사용자 스토리라인 정리를 지운다. docs/specs/user.md §4
 * <p>
 * 비동기 · 새 트랜잭션이고 다시 처리해도 결과가 같다. 역할 애너테이션이 없어 두 역할 모두에서 켜진다(platform-R9).
 */
@Component
class SummaryArticleRemovalListener {

	private final SummaryArticleRemoval removal;

	SummaryArticleRemovalListener(SummaryArticleRemoval removal) {
		this.removal = removal;
	}

	@ApplicationModuleListener
	public void on(ArticlesRemoved event) {
		this.removal.apply(event.articleIds());
	}

}
