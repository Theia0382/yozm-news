package net.yozmnews.user;

import java.util.Objects;
import java.util.Set;

import net.yozmnews.storyline.StorylineId;
import org.jspecify.annotations.Nullable;

/**
 * 사용자 스토리라인 하나 — 소비자 조회 {@link UserStorylineDirectory} 의 값. docs/specs/user.md §3
 *
 * @param id 저장된 사용자 스토리라인이면 그 ID — 아직 저장되지 않은 묶음이면 null
 * @param tops 추적 대상 — 공용 스토리라인의 포함 나무 꼭대기. 비지 않고, 한 사용자의 사용자 스토리라인 사이에 겹치지 않는다
 * @param tracked 흐름 추적(구독) 중인가
 */
public record UserStorylineView(@Nullable UserStorylineId id, Set<StorylineId> tops, boolean tracked) {

	/**
	 * @throws IllegalArgumentException 추적 대상이 없을 때
	 */
	public UserStorylineView {
		tops = Set.copyOf(Objects.requireNonNull(tops, "tops"));
		if (tops.isEmpty()) {
			throw new IllegalArgumentException("사용자 스토리라인은 추적 대상을 하나 이상 갖는다");
		}
	}

}
