package net.yozmnews.article.application;

import net.yozmnews.source.ArticleFetched;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * source 의 {@link ArticleFetched} 를 받는다 — 비동기 · 새 트랜잭션이고 이벤트 발행 레지스트리에 기록된다. 실패한 발행은
 * 반복 작업이 다시 처리하고, {@link ArticleIntake} 는 다시 불러도 결과가 같다.
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
