package net.yozmnews.article;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 기사 하나의 헤드라인 — 화면이 기사 한 줄로 보이는 정보. docs/specs/article.md §3 · article-R17.
 * <p>
 * 요약문은 담지 않는다.
 *
 * @param sources 출처 이름 — 엔트리 순서, 같은 이름은 한 번, 이름이 없는 피드는 뺀다. 비어 있을 수 있다
 * @param publishedAt 기사의 발행 시각 — {@link ArticleContent#publishedAt} 과 같다
 * @param canonicalUrl 기사의 정규 URL — 원문 링크
 */
public record ArticleHeadline(ArticleId id, String title, List<String> sources, Instant publishedAt,
		URI canonicalUrl) {

	public ArticleHeadline {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(title, "title");
		sources = List.copyOf(sources);
		Objects.requireNonNull(publishedAt, "publishedAt");
		Objects.requireNonNull(canonicalUrl, "canonicalUrl");
	}

}
