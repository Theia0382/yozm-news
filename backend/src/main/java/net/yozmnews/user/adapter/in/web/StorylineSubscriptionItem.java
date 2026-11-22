package net.yozmnews.user.adapter.in.web;

import java.time.Instant;

import net.yozmnews.storyline.StorylineState;
import net.yozmnews.user.application.StorylineSubscriptions.SubscribedStoryline;

/**
 * 구독 하나 — docs/specs/user.md §3 · user-R52. 스토리라인의 표시 정보(대표 기사 등)는 싣지 않는다 — 스토리라인 조회가 낸다.
 *
 * @param storylineId 스토리라인
 * @param subscribedAt 처음 구독한 시각
 * @param state 조회 시각의 상태(진행 · 잠잠 · 끝남)
 */
record StorylineSubscriptionItem(long storylineId, Instant subscribedAt, StorylineState state, long userStorylineId) {

	static StorylineSubscriptionItem of(SubscribedStoryline subscribed) {
		return new StorylineSubscriptionItem(subscribed.id().value(), subscribed.subscribedAt(), subscribed.state(),
				subscribed.userStoryline().value());
	}

}
