package net.yozmnews.source.domain.fetch;

/**
 * 피드 요청 관문 — docs/specs/source.md §4
 */
public interface ArticleSource {

	/** 피드를 한 번 받아 결과를 돌려준다 — docs/specs/source.md §4 */
	FetchResult fetch(FeedDescriptor feed, FetchState previous);

}
