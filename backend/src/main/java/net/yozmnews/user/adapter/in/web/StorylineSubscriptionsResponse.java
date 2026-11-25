package net.yozmnews.user.adapter.in.web;

import java.util.List;

import net.yozmnews.user.application.StorylineSubscriptions.SubscriptionsView;

/**
 * 추적 중인 사용자 스토리라인 — docs/specs/user.md §3 · user-R52
 *
 * @param subscriptions 사용자 스토리라인마다 한 줄 — 추적 시각 내림차순
 * @param limit 추적 중인 사용자 스토리라인 수의 상한
 */
record StorylineSubscriptionsResponse(List<StorylineSubscriptionItem> subscriptions, int limit) {

	static StorylineSubscriptionsResponse of(SubscriptionsView view) {
		return new StorylineSubscriptionsResponse(
				view.subscriptions().stream().map(StorylineSubscriptionItem::of).toList(), view.limit());
	}

}
