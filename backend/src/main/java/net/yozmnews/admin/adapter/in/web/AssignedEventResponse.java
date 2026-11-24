package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;

import net.yozmnews.event.EventStatus;
import net.yozmnews.event.EventView;

/**
 * 기사 상세의 배정된 사건 — 사건의 지금 값 · docs/specs/admin.md §5 admin-R8.
 *
 * @param articleCount 사건 전체의 기사 수
 */
record AssignedEventResponse(EventStatus status, int articleCount, Instant firstArticleAt, Instant lastArticleAt) {

	static AssignedEventResponse of(EventView event) {
		return new AssignedEventResponse(event.status(), event.articleCount(), event.firstArticleAt(),
				event.lastArticleAt());
	}

}
