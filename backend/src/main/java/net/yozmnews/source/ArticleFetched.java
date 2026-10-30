package net.yozmnews.source;

import java.time.Instant;
import java.util.Objects;

/**
 * 피드에서 새 항목을 받았다 — docs/specs/source.md §3
 */
public record ArticleFetched(SourceId sourceId, FeedId feedId, Instant fetchedAt, FetchedArticle article) {

	public ArticleFetched {
		Objects.requireNonNull(sourceId, "sourceId");
		Objects.requireNonNull(feedId, "feedId");
		Objects.requireNonNull(fetchedAt, "fetchedAt");
		Objects.requireNonNull(article, "article");
	}

}
