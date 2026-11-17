package net.yozmnews.reader.adapter.in.web;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;

/**
 * 독자 화면의 기사 정보 맞붙이기 — 정본이다 · docs/specs/reader.md §5 reader-R7.
 * <p>
 * {@code article} 의 {@link ArticleDirectory#headlines} 를 한 번 부른다 — 제목 · 출처 이름 · 발행 시각 · 원문 링크만 싣고 요약문은 싣지 않는다
 * (ADR-0068). 그새 지운 기사는 결과에 없다.
 */
final class ScreenArticles {

	private final ArticleDirectory articles;

	ScreenArticles(ArticleDirectory articles) {
		this.articles = Objects.requireNonNull(articles, "articles");
	}

	/** 기사들의 한 줄 — 빈 입력이면 부르지 않는다 */
	Map<ArticleId, NewsArticleResponse> of(Collection<ArticleId> ids) {
		List<ArticleId> distinct = ids.stream().distinct().toList();
		if (distinct.isEmpty()) {
			return Map.of();
		}
		Map<ArticleId, NewsArticleResponse> found = new LinkedHashMap<>();
		this.articles.headlines(distinct).forEach((id, headline) -> found.put(id, NewsArticleResponse.of(headline)));
		return found;
	}

}
