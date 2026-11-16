package net.yozmnews.user.domain.subscription;

import java.util.Objects;

import net.yozmnews.user.StorylineSubscriptionAction;

/**
 * 구독 이력에 남길 변경 하나 — 원인 · 시각은 기록할 때 함께 준다. docs/specs/user.md §4
 *
 * @param storylineId 스토리라인 ID
 * @param action 더함 · 뺌
 */
public record SubscriptionChange(long storylineId, StorylineSubscriptionAction action) {

	public SubscriptionChange {
		Objects.requireNonNull(action, "action");
	}

}
