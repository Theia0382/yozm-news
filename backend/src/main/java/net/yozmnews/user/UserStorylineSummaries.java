package net.yozmnews.user;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineId;

/**
 * 사용자 스토리라인의 정리 조회 — 독자 화면({@code reader})이 정리된 흐름을 엮을 때 읽는다. docs/specs/user.md §3
 * <p>
 * 정리가 있는 사용자 스토리라인만 낸다 — 무엇을 정리하고 지울지는 정리 작업이 정하고 이 조회는 저장한 행만 읽는다. 사용자를 인자로 받는 조회다 —
 * 화면 경로에서는 세션 사용자로만 부른다. 쓰지 않고, 사용자 스토리라인을 맞추지 않는다.
 */
public interface UserStorylineSummaries {

	/** {@link #ofTops} 한 번에 묻는 꼭대기 수의 상한 — 정본 */
	int MAX_TOPS = 100;

	/** 그 사용자의 정리된 사용자 스토리라인 — 마지막으로 정리한 시각 내림차순, 같으면 ID 순 */
	List<UserStorylineSummaryView> shown(UserId user);

	/** 그 사용자의 정리된 사용자 스토리라인 하나 — 다른 사용자의 것이거나 정리가 없으면 빈 값 */
	Optional<UserStorylineSummaryView> find(UserId user, UserStorylineId id);

	/**
	 * 꼭대기마다 그 꼭대기를 저장한 추적 대상으로 가진 정리된 사용자 스토리라인.
	 * @param tops 스토리라인 포함 나무의 꼭대기 — {@link #MAX_TOPS} 개까지(같은 것은 하나로 센다)
	 * @return 꼭대기 → 정리된 사용자 스토리라인 — 그런 것이 없는 꼭대기는 빠진다. 빈 입력이면 빈 맵
	 * @throws IllegalArgumentException 꼭대기가 상한보다 많을 때
	 */
	Map<StorylineId, UserStorylineSummaryRef> ofTops(UserId user, Collection<StorylineId> tops);

}
