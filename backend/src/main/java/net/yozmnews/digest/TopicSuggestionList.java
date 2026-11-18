package net.yozmnews.digest;

import java.util.List;
import java.util.Objects;

import net.yozmnews.tag.TagSetId;

/**
 * 한 사용자에게 지금 제안할 주제 — {@link TopicSuggestions} 가 낸다. docs/specs/digest.md §3 · digest-R42
 *
 * @param tagSet 키의 태그 체계 버전 — 화면이 수락 · 거절 요청에 싣는다
 * @param subtopics 제안할 활성 중분류 전체 키 — 관심 내림차순, 같으면 체계 순
 */
public record TopicSuggestionList(TagSetId tagSet, List<String> subtopics) {

	public TopicSuggestionList {
		Objects.requireNonNull(tagSet, "tagSet");
		subtopics = List.copyOf(Objects.requireNonNull(subtopics, "subtopics"));
	}

}
