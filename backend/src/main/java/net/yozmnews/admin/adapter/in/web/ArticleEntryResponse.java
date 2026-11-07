package net.yozmnews.admin.adapter.in.web;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 평가 상세의 기사 엔트리 하나 — docs/specs/admin.md §5 admin-R6.
 *
 * @param sourceName 출처 이름 — 없으면 null
 * @param section 피드 섹션 — 없으면 null
 * @param categories 그 피드에서 처음 본 항목의 RSS 카테고리
 */
record ArticleEntryResponse(@Nullable String sourceName, @Nullable String section, List<String> categories) {

	static ArticleEntryResponse of(AdminArticles.Entry entry) {
		return new ArticleEntryResponse(entry.sourceName(), entry.section(), entry.categories());
	}

}
