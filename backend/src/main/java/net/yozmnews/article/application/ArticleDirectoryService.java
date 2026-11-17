package net.yozmnews.article.application;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleHeadline;
import net.yozmnews.article.ArticleId;
import net.yozmnews.article.domain.ArticleRepository;
import net.yozmnews.article.domain.HeadlineSources;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabel;
import net.yozmnews.source.FeedLabels;
import net.yozmnews.source.SourceId;
import org.springframework.stereotype.Service;

/**
 * 공개 조회 {@link ArticleDirectory} 의 구현 — docs/specs/article.md §4. 헤드라인의 출처 이름은 {@link HeadlineSources} 로 엮는다(article-R17). 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class ArticleDirectoryService implements ArticleDirectory {

	private final ArticleRepository repository;

	private final FeedLabels labels;

	ArticleDirectoryService(ArticleRepository repository, FeedLabels labels) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.labels = Objects.requireNonNull(labels, "labels");
	}

	@Override
	public Map<ArticleId, ArticleContent> findAll(Collection<ArticleId> ids) {
		return this.repository.findContents(ids);
	}

	@Override
	public Map<ArticleId, ArticleHeadline> headlines(Collection<ArticleId> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<ArticleId, ArticleContent> contents = this.repository.findContents(ids);
		if (contents.isEmpty()) {
			return Map.of();
		}
		Map<FeedId, FeedLabel> labels = this.labels
			.labels(HeadlineSources.feeds(contents.values().stream().map(ArticleContent::entries).toList()));
		Map<ArticleId, ArticleHeadline> headlines = new LinkedHashMap<>();
		contents.forEach((id, content) -> headlines.put(id, new ArticleHeadline(id, content.title(),
				HeadlineSources.names(content.entries(), labels), content.publishedAt(), content.canonicalUrl())));
		return Collections.unmodifiableMap(headlines);
	}

	@Override
	public List<ArticleId> firstSeenSince(Instant since) {
		return this.repository.findFirstSeenSince(since);
	}

	@Override
	public Map<ArticleId, Set<SourceId>> sources(Collection<ArticleId> ids) {
		return this.repository.findSources(ids);
	}

	@Override
	public Map<ArticleId, Instant> publishedAt(Collection<ArticleId> ids) {
		return this.repository.findPublishedAt(ids);
	}

}
