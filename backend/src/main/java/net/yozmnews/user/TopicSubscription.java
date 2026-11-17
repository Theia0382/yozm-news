package net.yozmnews.user;

import java.util.Objects;
import java.util.Set;

/**
 * 주제 구독 행 하나와 그 행이 덮는 중분류 — {@link DigestInterests#topics()} 의 원소. docs/specs/user.md §3
 *
 * @param key 사용자가 구독한 키 — 대분류 키 또는 중분류 전체 키
 * @param subtopics 그 행이 덮는 중분류 전체 키 — 대분류면 그 버전의 하위 중분류 전부, 중분류면 그 하나
 */
public record TopicSubscription(String key, Set<String> subtopics) {

	public TopicSubscription {
		Objects.requireNonNull(key, "key");
		subtopics = Set.copyOf(Objects.requireNonNull(subtopics, "subtopics"));
	}

}
