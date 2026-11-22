package net.yozmnews.user.adapter.in.web;

import java.time.Instant;
import java.util.List;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineState;
import net.yozmnews.user.application.StorylineSubscriptions.SubscribedUserStoryline;
import org.jspecify.annotations.Nullable;

/**
 * 추적 중인 흐름 하나 — docs/specs/user.md §3 · user-R52. 스토리라인의 표시 정보(대표 기사 등)는 싣지 않는다 — 스토리라인 조회가 낸다.
 *
 * @param userStorylineId 사용자 스토리라인
 * @param subscribedAt 추적하기 시작한 시각
 * @param storylineIds 지금 있는 추적 씨앗 — ID 순, 하나 이상
 * @param state 조회 시각의 상태(진행 · 잠잠 · 끝남) — 추적 씨앗 가운데 가장 이어지는 것
 * @param title 정리의 제목 — 정리가 없으면 null
 */
record StorylineSubscriptionItem(long userStorylineId, Instant subscribedAt, List<Long> storylineIds,
		StorylineState state, @Nullable String title) {

	static StorylineSubscriptionItem of(SubscribedUserStoryline subscribed) {
		return new StorylineSubscriptionItem(subscribed.id().value(), subscribed.subscribedAt(),
				subscribed.storylines().stream().map(StorylineId::value).toList(), subscribed.state(),
				subscribed.title());
	}

}
