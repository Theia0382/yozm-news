package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.event.ArticleKeyTerms;

/**
 * 기사 상세의 표현 하나의 핵심 말 · docs/specs/admin.md §5 admin-R8.
 *
 * @param modelVersion 표현을 만든 임베딩 모델 버전
 * @param terms 핵심 말 — 처음 나온 순서, 비어 있을 수 있다
 */
record ArticleKeyTermsResponse(String modelVersion, List<String> terms, Instant createdAt) {

	static ArticleKeyTermsResponse of(ArticleKeyTerms keyTerms) {
		return new ArticleKeyTermsResponse(keyTerms.modelVersion(), keyTerms.terms(), keyTerms.createdAt());
	}

}
