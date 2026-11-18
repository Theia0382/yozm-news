package net.yozmnews.user;

import java.util.List;
import java.util.Objects;

import net.yozmnews.tag.TagSetId;

/**
 * 기간의 주제 구독 이력 — {@link TopicSubscriptionHistory#changes} 가 낸다. docs/specs/user.md §3 · user-R58
 *
 * @param tagSet 행마다 낸 중분류 키의 활성 태그 체계 버전 — 부르는 쪽이 기사 평가 키를 푼 계보 표의 버전과 맞춘다
 * @param changes 이력 행 — 시각 · 행 순
 */
public record TopicSubscriptionChanges(TagSetId tagSet, List<TopicSubscriptionChange> changes) {

	public TopicSubscriptionChanges {
		Objects.requireNonNull(tagSet, "tagSet");
		changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
	}

}
