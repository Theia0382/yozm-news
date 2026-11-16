package net.yozmnews.user.domain.subscription;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.StorylineSubscriptionCause;

/**
 * 스토리라인 구독 저장소 — 현재 상태({@code user_storyline_subscriptions})와 추가 전용 이력
 * ({@code user_storyline_subscription_events}). docs/specs/user.md §4 · §6
 * <p>
 * 쓰기는 사용자의 설정 행을 잠근 트랜잭션 안에서 부른다(user-R46 · user-R53). 현재 상태를 바꾸는 메서드는 이력을 쓰지 않는다 — 이력은
 * {@link #record} 로 따로 남긴다.
 */
public interface StorylineSubscriptionRepository {

	/** 사용자의 지금 구독 행 — 스토리라인 ID → 구독 시각 */
	Map<Long, Instant> load(UserId user);

	/** 사용자의 구독 행을 통째로 바꾼다 — 없어진 행은 지우고 새 행은 넣으며, 남는 행의 구독 시각은 {@code next} 의 값이다 */
	void replace(UserId user, Map<Long, Instant> next);

	/** @return 행이 없어 넣었으면 true — 이미 있으면 아무것도 하지 않는다 */
	boolean add(UserId user, long storylineId, Instant at);

	/** @return 행이 있어 지웠으면 true */
	boolean remove(UserId user, long storylineId);

	/** 변경을 이력에 남긴다 — 순서대로, 같은 원인 · 시각으로 */
	void record(UserId user, List<SubscriptionChange> changes, StorylineSubscriptionCause cause, Instant at);

	/**
	 * 이 스토리라인 가운데 하나라도 구독한 사용자 — 백그라운드 작업(따라가기) 전용.
	 * @return 사용자 ID 가 {@code afterUserId} 보다 큰 것을 ID 순으로 {@code limit} 명까지, 중복 없음
	 */
	List<UserId> subscribersOf(Collection<Long> storylineIds, long afterUserId, int limit);

	/** 사용자의 이력 가운데 시각이 {@code from} 이상 · {@code to} 미만인 행 — 시각 · 행 순 */
	List<StoredSubscriptionChange> changes(UserId user, Instant from, Instant to);

}
