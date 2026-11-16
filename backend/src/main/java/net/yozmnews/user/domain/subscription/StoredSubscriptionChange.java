package net.yozmnews.user.domain.subscription;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.user.StorylineSubscriptionAction;
import net.yozmnews.user.StorylineSubscriptionCause;

/**
 * 기록된 구독 이력 행 하나 — 읽기 값. docs/specs/user.md §4 · §6
 *
 * @param storylineId 기록된 스토리라인 ID — 기록 뒤에 합쳐졌거나 지워졌을 수 있다
 * @param action 더함 · 뺌
 * @param cause 원인
 * @param occurredAt 기록한 시각
 */
public record StoredSubscriptionChange(long storylineId, StorylineSubscriptionAction action,
		StorylineSubscriptionCause cause, Instant occurredAt) {

	public StoredSubscriptionChange {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(cause, "cause");
		Objects.requireNonNull(occurredAt, "occurredAt");
	}

}
