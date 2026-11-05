package net.yozmnews.event.application;

import java.time.InstantSource;

import net.yozmnews.article.ArticleStored;
import net.yozmnews.article.ArticlesRemoved;
import net.yozmnews.event.domain.feature.MatchFeaturesRepository;
import net.yozmnews.event.domain.intake.IntakeQueueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * {@link ArticleStored} · {@link ArticlesRemoved} 리스너 — docs/specs/event.md §4 · event-R5 · event-R8.
 * <p>
 * 비동기 · 새 트랜잭션이고 다시 처리해도 결과가 같다. 임베더 · Nori 를 쓰지 않는다. 지울 때는 대기 행을 먼저 지운다 — 진행 중인 회차의 쓰기
 * 트랜잭션이 그 대기 행을 잡고 있으면 그 커밋을 기다린 뒤 표현 행을 지워, 커밋 직전의 표현을 놓치지 않는다.
 */
@Component
class EventIntakeListener {

	private final IntakeQueueRepository queue;

	private final MatchFeaturesRepository features;

	private final InstantSource clock;

	@Autowired
	EventIntakeListener(IntakeQueueRepository queue, MatchFeaturesRepository features) {
		this(queue, features, InstantSource.system());
	}

	EventIntakeListener(IntakeQueueRepository queue, MatchFeaturesRepository features, InstantSource clock) {
		this.queue = queue;
		this.features = features;
		this.clock = clock;
	}

	@ApplicationModuleListener
	public void on(ArticleStored event) {
		this.queue.enqueue(event.articleId(), this.clock.instant());
	}

	@ApplicationModuleListener
	public void on(ArticlesRemoved event) {
		this.queue.delete(event.articleIds());
		this.features.deleteByArticles(event.articleIds());
	}

}
