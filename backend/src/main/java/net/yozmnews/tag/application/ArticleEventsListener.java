package net.yozmnews.tag.application;

import java.time.InstantSource;

import net.yozmnews.article.ArticleStored;
import net.yozmnews.article.ArticlesRemoved;
import net.yozmnews.tag.domain.queue.EvaluationQueueRepository;
import net.yozmnews.tag.domain.result.EvaluationResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * {@link ArticleStored} · {@link ArticlesRemoved} 리스너 — docs/specs/tag.md §4 · tag-R18 · tag-R21.
 * <p>
 * 새 기사를 평가 대기에 넣고, 지운 기사의 대기 · 평가 · 태그 행을 지운다. 비동기 · 새 트랜잭션이고 다시 처리해도 결과가 같다. 지울 때는
 * 대기 행을 먼저 지운다 — 진행 중인 저장 트랜잭션이 그 대기 행을 잡고 있으면 그 커밋을 기다린 뒤 결과 행을 지워, 커밋 직전의 평가 행을
 * 놓치지 않는다. 판정 원본은 evaluation 의 리스너가 따로 지운다(evaluation-R44).
 */
@Component
class ArticleEventsListener {

	private final EvaluationQueueRepository queue;

	private final EvaluationResultRepository results;

	private final InstantSource clock;

	@Autowired
	ArticleEventsListener(EvaluationQueueRepository queue, EvaluationResultRepository results) {
		this(queue, results, InstantSource.system());
	}

	ArticleEventsListener(EvaluationQueueRepository queue, EvaluationResultRepository results, InstantSource clock) {
		this.queue = queue;
		this.results = results;
		this.clock = clock;
	}

	@ApplicationModuleListener
	public void on(ArticleStored event) {
		this.queue.enqueue(event.articleId(), this.clock.instant());
	}

	@ApplicationModuleListener
	public void on(ArticlesRemoved event) {
		this.queue.delete(event.articleIds());
		this.results.deleteByArticles(event.articleIds());
	}

}
