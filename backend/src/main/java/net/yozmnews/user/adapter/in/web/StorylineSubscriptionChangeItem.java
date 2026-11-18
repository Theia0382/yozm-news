package net.yozmnews.user.adapter.in.web;

import java.time.Instant;

import net.yozmnews.user.StorylineSubscriptionAction;
import net.yozmnews.user.application.StorylineSubscriptionHistory.HistoryEntry;
import org.jspecify.annotations.Nullable;

/**
 * 구독 · 해제 하나 — docs/specs/user.md §3 · user-R63. 스토리라인의 표시 정보는 싣지 않는다 — 스토리라인 조회가 낸다.
 *
 * @param storylineId 그 스토리라인을 지금 푼 ID(합쳐졌으면 남는 쪽) — 지워졌으면 null
 * @param action 구독(ADD) · 해제(REMOVE)
 * @param occurredAt 구독 · 해제한 시각
 */
record StorylineSubscriptionChangeItem(@Nullable Long storylineId, StorylineSubscriptionAction action,
		Instant occurredAt) {

	static StorylineSubscriptionChangeItem of(HistoryEntry entry) {
		return new StorylineSubscriptionChangeItem((entry.current() == null) ? null : entry.current().value(),
				entry.action(), entry.occurredAt());
	}

}
