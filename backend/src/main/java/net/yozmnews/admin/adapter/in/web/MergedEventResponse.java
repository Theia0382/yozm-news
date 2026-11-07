package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;

import net.yozmnews.event.MergedEvent;
import org.jspecify.annotations.Nullable;

/**
 * 사건 상세의 합쳐진 사건 하나 — 값은 합칠 때의 것 · docs/specs/admin.md §5 admin-R7.
 *
 * @param probability 병합 판정 확률 — 기록이 없으면 null
 */
record MergedEventResponse(long id, int articleCount, Instant firstArticleAt, Instant lastArticleAt, Instant createdAt,
		Instant mergedAt, @Nullable Double probability) {

	static MergedEventResponse of(MergedEvent event) {
		return new MergedEventResponse(event.id().value(), event.articleCount(), event.firstArticleAt(),
				event.lastArticleAt(), event.createdAt(), event.mergedAt(), event.probability());
	}

}
