package net.yozmnews.user.adapter.in.web;

import java.util.List;

import net.yozmnews.user.application.StorylineSubscriptionHistory.HistoryPage;
import org.jspecify.annotations.Nullable;

/**
 * 내 구독 이력 한 쪽 — docs/specs/user.md §3 · user-R63
 *
 * @param changes 사용자가 직접 한 구독 · 해제, 최근부터
 * @param next 다음 쪽 커서 — 더 앞선 행이 없으면 null
 */
record StorylineSubscriptionChangesResponse(List<StorylineSubscriptionChangeItem> changes,
		@Nullable StorylineSubscriptionChangeCursor next) {

	static StorylineSubscriptionChangesResponse of(HistoryPage page) {
		return new StorylineSubscriptionChangesResponse(
				page.entries().stream().map(StorylineSubscriptionChangeItem::of).toList(),
				(page.next() == null) ? null : StorylineSubscriptionChangeCursor.of(page.next()));
	}

}
