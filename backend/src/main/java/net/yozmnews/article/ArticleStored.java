package net.yozmnews.article;

import java.util.Objects;

/**
 * 새 기사를 저장했다 — docs/specs/article.md §3
 */
public record ArticleStored(ArticleId articleId) {

	public ArticleStored {
		Objects.requireNonNull(articleId, "articleId");
	}

}
