package net.yozmnews.article;

import java.util.HashSet;
import java.util.List;

/**
 * 기사를 지웠다 — 운영자가 소스 · 피드를 지워 엔트리가 남지 않은 기사 · docs/specs/article.md §3
 *
 * @param articleIds 지운 기사 — 비어 있지 않고 중복이 없다
 */
public record ArticlesRemoved(List<ArticleId> articleIds) {

	/**
	 * @throws IllegalArgumentException 목록이 비었거나 같은 기사가 두 번 있을 때
	 */
	public ArticlesRemoved {
		articleIds = List.copyOf(articleIds);
		if (articleIds.isEmpty()) {
			throw new IllegalArgumentException("지운 기사가 없다");
		}
		if (new HashSet<>(articleIds).size() != articleIds.size()) {
			throw new IllegalArgumentException("같은 기사가 두 번 있다");
		}
	}

}
