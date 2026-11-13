package net.yozmnews.user.adapter.in.web;

import java.util.List;

import net.yozmnews.user.domain.notice.FeedStatusContent;

/**
 * 피드 상태 알림의 본문 — 피드마다 한 줄, 피드 ID 순. 피드 식별 · 상태 · 시각 · 횟수만 담는다. docs/specs/user.md §3 · user-R23
 *
 * @param feeds 피드마다 한 줄
 */
record FeedStatusNoticeResponse(List<FeedStatusEntryResponse> feeds) {

	static FeedStatusNoticeResponse of(FeedStatusContent content) {
		return new FeedStatusNoticeResponse(content.feeds()
			.stream()
			.map(entry -> new FeedStatusEntryResponse(entry.feedId().value(), entry.sourceName(), entry.section(),
					entry.firstFrom(), entry.lastTo(), entry.firstAt(), entry.lastAt(), entry.changes()))
			.toList());
	}

}
