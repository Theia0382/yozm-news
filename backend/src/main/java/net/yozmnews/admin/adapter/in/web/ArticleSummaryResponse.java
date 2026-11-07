package net.yozmnews.admin.adapter.in.web;

import java.util.List;

/**
 * 운영자 응답 행의 기사 정보 — 제목과 출처 · docs/specs/admin.md §5 admin-R6.
 *
 * @param sources 출처 이름 — 엔트리 순서, 같은 이름은 한 번, 이름이 없는 피드는 뺀다
 */
record ArticleSummaryResponse(String title, List<String> sources) {

	static ArticleSummaryResponse of(AdminArticles.Summary summary) {
		return new ArticleSummaryResponse(summary.title(), summary.sources());
	}

}
