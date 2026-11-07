package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;

import net.yozmnews.event.DecidedBy;
import net.yozmnews.event.EventArticleView;
import org.jspecify.annotations.Nullable;

/**
 * 사건 상세의 기사 하나 — 배정과 기사 정보 · docs/specs/admin.md §5 admin-R7.
 *
 * @param article 제목 · 출처 — 그새 지운 기사면 null
 * @param score 붙인 후보의 점수, 새 사건이면 가장 높은 후보의 점수 — 후보가 없었으면 null
 * @param decidedBy 배정을 정한 것 — 산식 · 판정 · 판정 실패
 */
record EventArticleResponse(long articleId, @Nullable ArticleSummaryResponse article, Instant publishedAt,
		@Nullable Double score, DecidedBy decidedBy, Instant assignedAt) {

	static EventArticleResponse of(EventArticleView view, AdminArticles.@Nullable Summary article) {
		return new EventArticleResponse(view.articleId().value(),
				(article == null) ? null : ArticleSummaryResponse.of(article), view.publishedAt(), view.score(),
				view.decidedBy(), view.assignedAt());
	}

}
