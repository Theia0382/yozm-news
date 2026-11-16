package net.yozmnews.admin.adapter.in.web;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;

/**
 * 운영자 응답의 목록 · 표본 행 기사 정보 맞붙이기(평가 검수 · 사건 · 스토리라인 · 큰 스토리라인 · 판정 기록) — 정본이다 · docs/specs/admin.md §5
 * admin-R6.
 * <p>
 * 기사 ID 를 모아 {@code article} 의 {@link ArticleDirectory#headlines} 를 한 번 부른다(출처 이름 엮기는 article-R17). 그새 지운 기사는 결과에
 * 없다 — 응답의 기사 정보가 {@code null} 이다. 상세의 기사는 {@link AdminArticleDetails} 가 낸다.
 */
final class AdminArticles {

	private final ArticleDirectory articles;

	AdminArticles(ArticleDirectory articles) {
		this.articles = Objects.requireNonNull(articles, "articles");
	}

	/** 목록 · 표본 행의 기사 — 제목과 출처 이름(헤드라인 — article-R17) */
	Map<ArticleId, Summary> summaries(Collection<ArticleId> ids) {
		Map<ArticleId, Summary> summaries = new HashMap<>();
		this.articles.headlines(ids)
			.forEach((id, headline) -> summaries.put(id, new Summary(headline.title(), headline.sources())));
		return summaries;
	}

	/**
	 * 목록 · 표본 행의 기사.
	 * @param sources 출처 이름 — 엔트리 순서, 같은 이름은 한 번
	 */
	record Summary(String title, List<String> sources) {
	}

}
