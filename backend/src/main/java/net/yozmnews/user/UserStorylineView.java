package net.yozmnews.user;

import java.util.Objects;
import java.util.Set;

import net.yozmnews.storyline.StorylineId;
import org.jspecify.annotations.Nullable;

/**
 * 사용자 스토리라인 하나 — 소비자 조회 {@link UserStorylineDirectory} 의 값. docs/specs/user.md §3
 *
 * @param id 저장된 사용자 스토리라인이면 그 ID — 아직 저장되지 않은 묶음이면 null
 * @param head 헤드 — 추적 대상 가운데 뒤 스토리라인이 없는 꼭대기. 사용자 스토리라인마다 하나이고 한 사용자의 사용자 스토리라인 사이에 겹치지 않는다
 * @param tops 추적 대상 — 공용 스토리라인의 포함 나무 꼭대기. 비지 않는다. 갈라지기 전 꼭대기는 한 사용자의 사용자 스토리라인 여럿에 든다
 * @param tracked 흐름 추적(구독) 중인가
 */
public record UserStorylineView(@Nullable UserStorylineId id, StorylineId head, Set<StorylineId> tops, boolean tracked) {

	/**
	 * @throws IllegalArgumentException 추적 대상이 없거나 헤드가 추적 대상에 없을 때
	 */
	public UserStorylineView {
		Objects.requireNonNull(head, "head");
		tops = Set.copyOf(Objects.requireNonNull(tops, "tops"));
		if (tops.isEmpty() || !tops.contains(head)) {
			throw new IllegalArgumentException("사용자 스토리라인은 헤드를 든 추적 대상을 하나 이상 갖는다: " + head + " · " + tops);
		}
	}

}
