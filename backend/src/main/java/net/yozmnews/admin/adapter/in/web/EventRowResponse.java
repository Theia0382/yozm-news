package net.yozmnews.admin.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventStatus;
import net.yozmnews.event.EventSummary;
import org.jspecify.annotations.Nullable;

/**
 * 사건 목록의 한 줄 — docs/specs/admin.md §5 admin-R7.
 *
 * @param articleCount 사건 전체의 기사 수
 * @param periodArticles 목록 기간 안 기사 수
 * @param periodSources 목록 기간 안 기사의 서로 다른 소스 수
 * @param importance 목록 기간 안 기사의 중요도 평균 — 중요도가 있는 기사가 없으면 null
 * @param representativeId 사건 기사 전체에서 고른 대표 기사 — 배정이 없으면 null
 * @param representative 대표 기사의 제목 · 출처 — 대표가 없거나 그새 지웠으면 null
 */
record EventRowResponse(long id, EventStatus status, int articleCount, Instant firstArticleAt, Instant lastArticleAt,
		int periodArticles, int periodSources, @Nullable BigDecimal importance,
		@Nullable Long representativeId, @Nullable ArticleSummaryResponse representative) {

	static EventRowResponse of(EventSummary summary, AdminArticles.@Nullable Summary representative) {
		ArticleId id = summary.representative();
		return new EventRowResponse(summary.event().id().value(), summary.event().status(),
				summary.event().articleCount(), summary.event().firstArticleAt(), summary.event().lastArticleAt(),
				summary.tally().articles(), summary.tally().sources(), summary.tally().importance(),
				(id == null) ? null : id.value(),
				(representative == null) ? null : ArticleSummaryResponse.of(representative));
	}

}
