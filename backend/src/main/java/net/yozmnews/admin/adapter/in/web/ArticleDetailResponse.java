package net.yozmnews.admin.adapter.in.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 평가 상세 · 기사 상세의 기사 정보 — 요약문 원문은 운영자 화면에만 보인다(ADR-0068) · docs/specs/admin.md §5 admin-R6.
 *
 * @param summary 저장된 RSS 요약문 — 없으면 null
 * @param canonicalUrl 원문 링크(정규 URL)
 * @param publishedAt 기사의 발행 시각
 * @param entries 엔트리마다 — 받은 시각 순
 */
record ArticleDetailResponse(String title, @Nullable String summary, URI canonicalUrl, Instant publishedAt,
		List<ArticleEntryResponse> entries) {

	static ArticleDetailResponse of(AdminArticleDetails.Detail detail) {
		return new ArticleDetailResponse(detail.title(), detail.summary(), detail.canonicalUrl(), detail.publishedAt(),
				detail.entries().stream().map(ArticleEntryResponse::of).toList());
	}

}
