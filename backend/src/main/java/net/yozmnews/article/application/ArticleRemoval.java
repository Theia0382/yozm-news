package net.yozmnews.article.application;

import java.util.List;

import net.yozmnews.article.ArticleId;
import net.yozmnews.article.domain.ArticleRepository;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지운 소스 · 피드의 기사 쪽 삭제 — docs/specs/article.md §4 · article-R15.
 * <p>
 * 소스 잠금 안에서 그 소스(피드)의 엔트리를 지우고, 지운 엔트리의 기사 가운데 엔트리가 남지 않은 기사를 지운다. 다른 소스 · 피드도 가진 기사는
 * 남는다. 다시 처리해도 결과가 같다.
 */
@Service
class ArticleRemoval {

	private static final Logger logger = LoggerFactory.getLogger(ArticleRemoval.class);

	private final ArticleRepository repository;

	ArticleRemoval(ArticleRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public void removeSource(SourceId sourceId) {
		this.repository.lockSource(sourceId);
		List<ArticleId> touched = this.repository.deleteEntries(sourceId);
		int articles = this.repository.deleteArticlesWithoutEntries(touched);
		logger.info("지운 소스의 엔트리 {}건 · 기사 {}건을 지웠다 — 소스 {}", touched.size(), articles, sourceId.value());
	}

	@Transactional
	public void removeFeed(SourceId sourceId, FeedId feedId) {
		this.repository.lockSource(sourceId);
		List<ArticleId> touched = this.repository.deleteEntries(sourceId, feedId);
		int articles = this.repository.deleteArticlesWithoutEntries(touched);
		logger.info("지운 피드의 엔트리 {}건 · 기사 {}건을 지웠다 — 소스 {}, 피드 {}", touched.size(), articles,
				sourceId.value(), feedId.value());
	}

}
