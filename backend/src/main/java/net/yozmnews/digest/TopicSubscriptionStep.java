package net.yozmnews.digest;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.user.TopicSubscriptionAction;

/**
 * 사용자가 직접 바꾼 주제 구독 하나 — 관심사 히스토리의 기간마다 붙는다. docs/specs/digest.md §3 · digest-R31
 *
 * @param at 바꾼 시각
 * @param action 더함 · 뺌
 * @param subtopics 그 행이 덮던 중분류를 활성 키로 푼 것 — 비지 않는다
 */
public record TopicSubscriptionStep(Instant at, TopicSubscriptionAction action, Set<String> subtopics) {

	public TopicSubscriptionStep {
		Objects.requireNonNull(at, "at");
		Objects.requireNonNull(action, "action");
		subtopics = Set.copyOf(Objects.requireNonNull(subtopics, "subtopics"));
		if (subtopics.isEmpty()) {
			throw new IllegalArgumentException("주제 구독 변경의 중분류가 비었다");
		}
	}

}
