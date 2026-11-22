package net.yozmnews.user.domain.subscription;

import java.time.Instant;
import java.util.List;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.StorylineSubscriptionCause;
import org.jspecify.annotations.Nullable;

/**
 * 스토리라인 구독 이력 저장소 — 추가 전용 이력({@code user_storyline_subscription_events}). 현재 상태(구독 행 · 추적 씨앗)는
 * {@code UserStorylineRepository} 가 갖는다. docs/specs/user.md §4 · §6
 * <p>
 * 쓰기는 사용자의 설정 행을 잠근 트랜잭션 안에서 부른다(user-R46 · user-R53).
 */
public interface StorylineSubscriptionRepository {

	/** 변경을 이력에 남긴다 — 순서대로, 같은 원인 · 시각으로 */
	void record(UserId user, List<SubscriptionChange> changes, StorylineSubscriptionCause cause, Instant at);

	/** 사용자의 이력 가운데 시각이 {@code from} 이상 · {@code to} 미만인 행 — 시각 · 행 순 */
	List<StoredSubscriptionChange> changes(UserId user, Instant from, Instant to);

	/**
	 * 사용자의 이력 가운데 원인이 사용자인 행 — 시각 · 행의 내림차순으로 {@code limit} 건까지. 내 구독 이력 전용(user-R63).
	 * @param before 있으면 이 순서 키보다 앞선(시각이 이르거나, 같으면 행 ID 가 작은) 행만
	 */
	List<UserSubscriptionChange> userChanges(UserId user, @Nullable SubscriptionChangeCursor before, int limit);

}
