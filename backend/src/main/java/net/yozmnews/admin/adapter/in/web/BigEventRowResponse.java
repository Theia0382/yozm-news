package net.yozmnews.admin.adapter.in.web;

import java.math.BigDecimal;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.BigEvent;
import org.jspecify.annotations.Nullable;

/**
 * 기간 안의 큰 사건 하나 — 기간 안 집계와 사건 전체의 대표 기사 · docs/specs/admin.md §5 admin-R7.
 *
 * @param articles 기간 안 기사 수
 * @param sources 기간 안 기사의 서로 다른 소스 수
 * @param importance 기간 안 중요도 평균 — 중요도가 있는 기사가 없으면 null
 * @param representativeId 사건 기사 전체에서 고른 대표 기사 — 없으면 null
 * @param representative 대표 기사의 제목 · 출처 — 대표가 없거나 그새 지웠으면 null
 */
record BigEventRowResponse(long id, int articles, int sources, @Nullable BigDecimal importance,
		@Nullable Long representativeId, @Nullable ArticleSummaryResponse representative) {

	static BigEventRowResponse of(BigEvent event, @Nullable ArticleId representativeId,
			AdminArticles.@Nullable Summary representative) {
		return new BigEventRowResponse(event.id().value(), event.tally().articles(), event.tally().sources(),
				event.tally().importance(), (representativeId == null) ? null : representativeId.value(),
				(representative == null) ? null : ArticleSummaryResponse.of(representative));
	}

}
