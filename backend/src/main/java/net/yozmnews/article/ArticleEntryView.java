package net.yozmnews.article;

import java.util.List;
import java.util.Objects;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;

/**
 * 기사의 엔트리 하나 — 그 기사를 본 피드와 그 피드 항목의 카테고리 · docs/specs/article.md §3.
 *
 * @param categories 그 피드에서 처음 본 항목의 RSS 카테고리 — 피드 순서 그대로
 */
public record ArticleEntryView(SourceId sourceId, FeedId feedId, List<String> categories) {

	public ArticleEntryView {
		Objects.requireNonNull(sourceId, "sourceId");
		Objects.requireNonNull(feedId, "feedId");
		categories = List.copyOf(categories);
	}

}
