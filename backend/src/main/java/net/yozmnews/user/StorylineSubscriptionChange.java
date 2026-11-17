package net.yozmnews.user;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.storyline.StorylineId;
import org.jspecify.annotations.Nullable;

/**
 * 스토리라인 구독 이력 행 하나 — 소비자 조회의 값. docs/specs/user.md §3 · user-R55
 *
 * @param recorded 기록된 스토리라인 ID — 기록 뒤에 합쳐졌거나 지워졌을 수 있다
 * @param current recorded 를 지금 푼 스토리라인 ID(합쳐졌으면 남는 쪽) — 지워졌으면 null
 * @param action 더함 · 뺌
 * @param cause 원인 — 관심 신호는 {@link StorylineSubscriptionCause#USER} 인 행만이다
 * @param occurredAt 기록한 시각
 */
public record StorylineSubscriptionChange(StorylineId recorded, @Nullable StorylineId current,
		StorylineSubscriptionAction action, StorylineSubscriptionCause cause, Instant occurredAt) {

	public StorylineSubscriptionChange {
		Objects.requireNonNull(recorded, "recorded");
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(cause, "cause");
		Objects.requireNonNull(occurredAt, "occurredAt");
	}

}
