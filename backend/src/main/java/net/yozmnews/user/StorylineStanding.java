package net.yozmnews.user;

import java.util.Objects;
import java.util.Set;

import net.yozmnews.storyline.StorylineId;

/**
 * 사용자 하나의 지금 스토리라인 판정 — 스토리라인 포함 나무의 꼭대기로 낸다. docs/specs/user.md §3
 * <p>
 * 두 집합은 따로 판정한 것이다 — 추적 중이라 관심 스토리라인인 꼭대기는 남길 스토리라인에 없을 수 있다.
 *
 * @param followed 관심 스토리라인의 꼭대기 — 정리를 새로 만들 수 있는 스토리라인
 * @param kept 남길 스토리라인의 꼭대기 — 이미 정리한 스토리라인을 남기는 기준
 */
public record StorylineStanding(Set<StorylineId> followed, Set<StorylineId> kept) {

	public StorylineStanding {
		followed = Set.copyOf(Objects.requireNonNull(followed, "followed"));
		kept = Set.copyOf(Objects.requireNonNull(kept, "kept"));
	}

}
