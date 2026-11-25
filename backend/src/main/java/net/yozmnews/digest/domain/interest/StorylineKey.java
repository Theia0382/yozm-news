package net.yozmnews.digest.domain.interest;

import java.util.Set;
import java.util.stream.Collectors;

import net.yozmnews.storyline.StorylineId;

/**
 * 반복을 가르는 스토리라인 키 — 꼭대기를 추적 대상으로 가진 사용자 스토리라인 묶음이 있으면 그 묶음들, 없으면 꼭대기 자신.
 * docs/specs/digest.md §5 · digest-R50
 * <p>
 * 스토리라인 관심의 키는 {@link UserStoryline} 뿐이다 — {@link Top} 은 반복 판정에만 쓴다.
 */
public sealed interface StorylineKey permits StorylineKey.UserStoryline, StorylineKey.Top {

	/** 사용자 스토리라인 묶음 — 추적 중인지와 무관하다 */
	record UserStoryline(int userStoryline) implements StorylineKey {
	}

	/** 묶음이 없는 꼭대기 */
	record Top(StorylineId top) implements StorylineKey {
	}

	/** 그 꼭대기의 키들 — 묶음이 있으면 묶음마다 하나, 없으면 꼭대기 자신 하나 */
	static Set<StorylineKey> of(StorylineId top, UserStorylines userStorylines) {
		Set<Integer> found = userStorylines.userStorylinesOf(top);
		if (found.isEmpty()) {
			return Set.of(new Top(top));
		}
		return found.stream().map(UserStoryline::new).collect(Collectors.toUnmodifiableSet());
	}

}
