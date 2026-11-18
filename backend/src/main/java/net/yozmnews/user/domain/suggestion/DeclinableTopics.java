package net.yozmnews.user.domain.suggestion;

import net.yozmnews.tag.TagSetView;
import net.yozmnews.user.domain.preference.TopicExpansion;

/**
 * 거절할 수 있는 키 — 그 버전의 주제 중분류 전체 키다. 대분류 키 · 다른 종류의 키 · 없는 키는 아니다. 판단의 정본 — docs/specs/user.md
 * user-R64. 중분류 키인지 보는 일은 {@link TopicExpansion#isSubtopic} 이 한다.
 */
public final class DeclinableTopics {

	private DeclinableTopics() {
	}

	/** 그 버전의 주제 중분류 전체 키인가 */
	public static boolean isSubtopic(TagSetView version, String key) {
		return TopicExpansion.isSubtopic(version, key);
	}

}
