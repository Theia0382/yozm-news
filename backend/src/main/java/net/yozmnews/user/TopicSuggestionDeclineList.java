package net.yozmnews.user;

import java.util.List;
import java.util.Objects;

import net.yozmnews.tag.TagSetId;

/**
 * 한 사용자의 구독 제안 거절 — {@link TopicSuggestionDeclines} 가 낸다. docs/specs/user.md §3 · user-R65
 *
 * @param tagSet 거절의 키를 푼 활성 버전 — 조회 뒤의 활성 전환은 부르는 쪽이 이 값으로 알아챈다
 * @param declines 거절 — 중분류마다 많아야 하나, 순서 없음
 */
public record TopicSuggestionDeclineList(TagSetId tagSet, List<TopicSuggestionDecline> declines) {

	public TopicSuggestionDeclineList {
		Objects.requireNonNull(tagSet, "tagSet");
		declines = List.copyOf(Objects.requireNonNull(declines, "declines"));
	}

}
