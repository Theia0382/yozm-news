package net.yozmnews.admin.adapter.in.web;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 기사 하나의 상세 — 기사 정보 · 평가 · 사건 소속 · 표현의 핵심 말 · docs/specs/admin.md §3 · admin-R8. 기사 ID 는 JSON 수다.
 *
 * @param article 기사의 제목 · 요약문 · 원문 링크 · 발행 시각 · 엔트리
 * @param evaluations 기사의 평가 — 버전마다 하나, 버전 번호 내림차순. 평가하지 않은 기사면 빈 목록
 * @param assignment 사건 소속 — 배정이 없는 기사면 null
 * @param keyTerms 기사 표현마다 핵심 말 — 만든 시각 · 모델 버전 순. 표현이 없으면 빈 목록
 */
record ArticleReviewResponse(long id, ArticleDetailResponse article, List<ArticleEvaluationResponse> evaluations,
		@Nullable ArticleAssignmentResponse assignment, List<ArticleKeyTermsResponse> keyTerms) {

}
