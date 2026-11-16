package net.yozmnews.reader.adapter.in.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import net.yozmnews.article.ArticleHeadline;

/**
 * 독자 화면의 기사 한 줄 — 제목 · 출처 이름 · 발행 시각 · 원문 링크. docs/specs/reader.md §5 reader-R7. 요약문은 싣지 않는다.
 *
 * @param sources 출처 이름 — 엔트리 순서, 같은 이름은 한 번(article-R17)
 * @param url 원문 링크(정규 URL)
 */
record NewsArticleResponse(String title, List<String> sources, Instant publishedAt, URI url) {

	static NewsArticleResponse of(ArticleHeadline headline) {
		return new NewsArticleResponse(headline.title(), headline.sources(), headline.publishedAt(),
				headline.canonicalUrl());
	}

}
