package net.yozmnews.article.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.article.domain.ArticleRepository;
import net.yozmnews.source.SourceId;
import org.springframework.stereotype.Service;

/**
 * 공개 조회 {@link ArticleDirectory} 의 구현 — docs/specs/article.md §4. 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class ArticleDirectoryService implements ArticleDirectory {

	private final ArticleRepository repository;

	ArticleDirectoryService(ArticleRepository repository) {
		this.repository = repository;
	}

	@Override
	public Map<ArticleId, ArticleContent> findAll(Collection<ArticleId> ids) {
		return this.repository.findContents(ids);
	}

	@Override
	public List<ArticleId> firstSeenSince(Instant since) {
		return this.repository.findFirstSeenSince(since);
	}

	@Override
	public Map<ArticleId, Set<SourceId>> sources(Collection<ArticleId> ids) {
		return this.repository.findSources(ids);
	}

}
