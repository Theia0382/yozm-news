package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;

import net.yozmnews.event.MergedEvent;
import org.jspecify.annotations.Nullable;

/**
 * 사건 상세의 합쳐진 사건 하나 — 값은 합칠 때의 것 · docs/specs/admin.md §5 admin-R7.
 *
 * @param representativeId 합치기 직전 그 사건의 대표 기사 — 남기지 않았으면 null
 * @param representative 대표 기사의 제목 · 출처 — 대표가 없거나 그새 지웠으면 null
 * @param probability 병합 판정 확률 — 기록이 없으면 null
 */
record MergedEventResponse(long id, int articleCount, Instant firstArticleAt, Instant lastArticleAt, Instant createdAt,
		Instant mergedAt, @Nullable Long representativeId, @Nullable ArticleSummaryResponse representative,
		@Nullable Double probability) {

	static MergedEventResponse of(MergedEvent event, AdminArticles.@Nullable Summary representative) {
		return new MergedEventResponse(event.id().value(), event.articleCount(), event.firstArticleAt(),
				event.lastArticleAt(), event.createdAt(), event.mergedAt(),
				(event.representative() == null) ? null : event.representative().value(),
				(representative == null) ? null : ArticleSummaryResponse.of(representative), event.probability());
	}

}
