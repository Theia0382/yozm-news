package net.yozmnews.article;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 기사 하나의 내용 — docs/specs/article.md §3.
 *
 * @param summary 저장된 RSS 요약문 — 없으면 null
 * @param canonicalUrl 기사의 정규 URL — 원문 링크, 언제나 http · https
 * @param publishedAt 기사의 발행 시각 — 처음 저장한 값(article-R8). 해석하지 못한 항목이면 받은 시각이다(source-R40)
 * @param entries 기사의 엔트리 — 받은 시각 순, 같으면 피드 ID 순
 */
public record ArticleContent(ArticleId id, String title, @Nullable String summary, URI canonicalUrl,
		Instant publishedAt, List<ArticleEntryView> entries) {

	public ArticleContent {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(canonicalUrl, "canonicalUrl");
		Objects.requireNonNull(publishedAt, "publishedAt");
		entries = List.copyOf(entries);
	}

}
