package net.yozmnews.user;

import java.time.Instant;
import java.util.List;

import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineId;

/**
 * 스토리라인 구독 조회 — 다이제스트 · 관심도 · 변화 리포트 작업이 읽는다. docs/specs/user.md §3 · user-R55
 * <p>
 * 사용자를 인자로 받는 조회다 — 화면 경로에서는 세션 사용자로만 부른다. 쓰지 않는다 — 저장된 구독 · 이력의 ID 는 사용자가 구독을 다룰
 * 때까지 합쳐진 옛 ID 일 수 있어, {@code storyline} 의 남는 쪽 풀기로 풀어 낸다. 기간을 사용자 시간대의 날로 자르는 일과 관심
 * 신호로 쓸 행을 고르는 일은 부르는 쪽이 한다.
 */
public interface StorylineSubscriptionDirectory {

	/**
	 * 지금 추적하는 스토리라인 — 추적 중인 사용자 스토리라인의 추적 씨앗(사용자가 누른 스토리라인)을 남는 스토리라인 ID 로 풀고 같은 것은
	 * 하나로, 사라진 것은 빼고, ID 순. 그 흐름이 이어짐으로 따라가는 뒤 흐름은 들지 않는다 — 그것은 {@link UserStorylineDirectory} 가
	 * 낸다
	 */
	List<StorylineId> subscriptions(UserId user);

	/**
	 * 이력 행 가운데 시각이 {@code from} 이상 · {@code to} 미만인 것 — 시각 · 행 순.
	 * @throws IllegalArgumentException {@code from} 이 {@code to} 보다 늦을 때
	 */
	List<StorylineSubscriptionChange> changes(UserId user, Instant from, Instant to);

}
