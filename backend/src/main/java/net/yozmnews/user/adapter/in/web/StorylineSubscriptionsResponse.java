package net.yozmnews.user.adapter.in.web;

import java.util.List;

import net.yozmnews.user.application.StorylineSubscriptions.SubscriptionsView;

/**
 * 내 스토리라인 구독 — docs/specs/user.md §3 · user-R52
 *
 * @param subscriptions 구독 시각 내림차순
 * @param limit 구독 상한
 */
record StorylineSubscriptionsResponse(List<StorylineSubscriptionItem> subscriptions, int limit) {

	static StorylineSubscriptionsResponse of(SubscriptionsView view) {
		return new StorylineSubscriptionsResponse(
				view.subscriptions().stream().map(StorylineSubscriptionItem::of).toList(), view.limit());
	}

}
