package net.yozmnews.user.domain.subscription;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.user.StorylineSubscriptionAction;

/**
 * 원인이 사용자인 구독 이력 행 하나 — 내 구독 이력이 읽는다. docs/specs/user.md §5 · user-R63
 *
 * @param id 이력 행 ID
 * @param storylineId 기록 당시의 스토리라인 ID — 그 뒤에 합쳐졌거나 지워졌을 수 있다
 * @param action 구독(더함) · 해제(뺌)
 * @param occurredAt 기록한 시각
 */
public record UserSubscriptionChange(long id, long storylineId, StorylineSubscriptionAction action,
		Instant occurredAt) {

	public UserSubscriptionChange {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(occurredAt, "occurredAt");
	}

	/** 이 행의 순서 키 */
	public SubscriptionChangeCursor cursor() {
		return new SubscriptionChangeCursor(this.occurredAt, this.id);
	}

}
