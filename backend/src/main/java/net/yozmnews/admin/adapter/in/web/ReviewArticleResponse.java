package net.yozmnews.admin.adapter.in.web;

import java.util.List;

/**
 * 목록 · 표본 행의 기사 정보 — docs/specs/admin.md §5 admin-R6.
 *
 * @param sources 출처 이름 — 엔트리 순서, 같은 이름은 한 번, 이름이 없는 피드는 뺀다
 */
record ReviewArticleResponse(String title, List<String> sources) {

	static ReviewArticleResponse of(ReviewArticles.Summary summary) {
		return new ReviewArticleResponse(summary.title(), summary.sources());
	}

}
