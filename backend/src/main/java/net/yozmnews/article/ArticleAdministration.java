package net.yozmnews.article;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;

/**
 * 기사 쪽 운영 기능 — 운영자 화면이 부르는 공개 API. docs/specs/article.md §3.
 * <p>
 * 구현은 {@code application} 의 {@code ArticleAdministrationService} 다.
 */
public interface ArticleAdministration {

	/** 지금 기준 최근 집계 기간의 피드별 · 칸별 엔트리 개수(article-R13) */
	EntryStatistics entryStatistics();

	/** 지금 기준 그 소스만 가진 기사 수 — 소스를 지우면 함께 지워진다(article-R15) */
	long exclusiveArticleCount(SourceId sourceId);

	/** 지금 기준 그 소스의 그 피드만 가진 기사 수 — 피드를 지우면 함께 지워진다(article-R15) */
	long exclusiveArticleCount(SourceId sourceId, FeedId feedId);

}
