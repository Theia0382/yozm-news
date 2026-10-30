package net.yozmnews.article;

/**
 * 기사 식별자 — docs/specs/article.md §3
 */
public record ArticleId(long value) {

	public ArticleId {
		if (value < 1) {
			throw new IllegalArgumentException("기사 식별자는 1 이상이어야 한다: " + value);
		}
	}

}
