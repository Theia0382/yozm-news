package net.yozmnews.user.adapter.in.web;

import java.time.Instant;

import net.yozmnews.user.domain.subscription.SubscriptionChangeCursor;

/**
 * 내 구독 이력의 다음 쪽 커서 — docs/specs/user.md §3 · user-R63. 다음 요청은 이 두 값을 {@code beforeAt} · {@code beforeId} 로
 * 넘긴다.
 *
 * @param occurredAt 이 쪽 마지막 행의 시각
 * @param id 이 쪽 마지막 행의 ID
 */
record StorylineSubscriptionChangeCursor(Instant occurredAt, long id) {

	static StorylineSubscriptionChangeCursor of(SubscriptionChangeCursor cursor) {
		return new StorylineSubscriptionChangeCursor(cursor.occurredAt(), cursor.id());
	}

}
