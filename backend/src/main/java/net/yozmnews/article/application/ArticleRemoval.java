package net.yozmnews.article.application;

import java.util.List;

import net.yozmnews.article.ArticleId;
import net.yozmnews.article.ArticlesRemoved;
import net.yozmnews.article.domain.ArticleRepository;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지운 소스 · 피드의 기사 쪽 삭제 — docs/specs/article.md §4 · article-R15.
 * <p>
 * 소스 잠금 안에서 그 소스(피드)의 엔트리를 지우고, 지운 엔트리의 기사 가운데 엔트리가 남지 않은 기사를 지운다. 다른 소스 · 피드도 가진 기사는
 * 남는다. 지운 기사가 있으면 같은 트랜잭션에서 {@link ArticlesRemoved} 를 발행한다. 다시 처리해도 결과가 같다.
 */
@Service
class ArticleRemoval {

	/** 이벤트 하나에 싣는 지운 기사의 상한 — 넘으면 나눠 여러 번 발행한다. 측정 전 목표다 */
	static final int MAX_IDS_PER_EVENT = 1_000;

	private static final Logger logger = LoggerFactory.getLogger(ArticleRemoval.class);

	private final ArticleRepository repository;

	private final ApplicationEventPublisher events;

	ArticleRemoval(ArticleRepository repository, ApplicationEventPublisher events) {
		this.repository = repository;
		this.events = events;
	}

	@Transactional
	public void removeSource(SourceId sourceId) {
		this.repository.lockSource(sourceId);
		List<ArticleId> touched = this.repository.deleteEntries(sourceId);
		List<ArticleId> removed = this.repository.deleteArticlesWithoutEntries(touched);
		publish(removed);
		logger.info("지운 소스의 엔트리 {}건 · 기사 {}건을 지웠다 — 소스 {}", touched.size(), removed.size(), sourceId.value());
	}

	@Transactional
	public void removeFeed(SourceId sourceId, FeedId feedId) {
		this.repository.lockSource(sourceId);
		List<ArticleId> touched = this.repository.deleteEntries(sourceId, feedId);
		List<ArticleId> removed = this.repository.deleteArticlesWithoutEntries(touched);
		publish(removed);
		logger.info("지운 피드의 엔트리 {}건 · 기사 {}건을 지웠다 — 소스 {}, 피드 {}", touched.size(), removed.size(),
				sourceId.value(), feedId.value());
	}

	private void publish(List<ArticleId> removed) {
		for (int from = 0; from < removed.size(); from += MAX_IDS_PER_EVENT) {
			this.events.publishEvent(
					new ArticlesRemoved(removed.subList(from, Math.min(from + MAX_IDS_PER_EVENT, removed.size()))));
		}
	}

}
