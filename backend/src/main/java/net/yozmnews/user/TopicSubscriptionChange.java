package net.yozmnews.user;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.tag.TagSetId;

/**
 * 주제 구독 이력 행 하나 — 소비자 조회 {@link TopicSubscriptionHistory} 의 값. docs/specs/user.md §3 · user-R58
 *
 * @param recordedKey 기록된 키 — 기록 당시 버전의 대분류 키 또는 중분류 전체 키
 * @param recordedVersion 기록 당시 태그 체계 버전
 * @param subtopics 그 행이 덮던 중분류(기록 당시 버전에서 대분류는 하위 전체로 펼침)를 활성 버전의 중분류로 푼 것 — 계보 없이 지운 것은
 *     빠지고, 모두 지웠으면 비었다
 * @param action 더함 · 뺌
 * @param cause 원인 — 관심 신호는 {@link TopicSubscriptionCause#USER} 인 행만이다
 * @param occurredAt 기록한 시각
 */
public record TopicSubscriptionChange(String recordedKey, TagSetId recordedVersion, Set<String> subtopics,
		TopicSubscriptionAction action, TopicSubscriptionCause cause, Instant occurredAt) {

	public TopicSubscriptionChange {
		Objects.requireNonNull(recordedKey, "recordedKey");
		Objects.requireNonNull(recordedVersion, "recordedVersion");
		subtopics = Set.copyOf(Objects.requireNonNull(subtopics, "subtopics"));
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(cause, "cause");
		Objects.requireNonNull(occurredAt, "occurredAt");
	}

}
