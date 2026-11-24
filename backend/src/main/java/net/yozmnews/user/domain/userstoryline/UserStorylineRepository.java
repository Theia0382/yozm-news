package net.yozmnews.user.domain.userstoryline;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import net.yozmnews.platform.UserId;

/**
 * 사용자 스토리라인 저장소 — 개체({@code user_storylines}) · 추적 대상({@code user_storyline_members}) · 추적 씨앗
 * ({@code user_storyline_tracked_seeds}) · 구독 행({@code user_storyline_subscriptions}). docs/specs/user.md §4 · §6
 * <p>
 * 쓰기는 사용자의 설정 행을 잠근 트랜잭션 안에서 부른다. 이력은 쓰지 않는다 — 이력은 구독 저장소의 {@code record} 로 따로 남긴다.
 */
public interface UserStorylineRepository {

	/** 사용자의 사용자 스토리라인 행 — ID 순 */
	List<UserStorylineRow> load(UserId user);

	/** @return 새 사용자 스토리라인의 ID — 추적 대상 · 추적 씨앗 · 구독 행은 없다 */
	long create(UserId user, Instant now);

	/** 사용자 스토리라인을 지운다 — 추적 대상 · 추적 씨앗 · 구독 행도 함께 */
	void delete(long id);

	/** 추적 대상을 통째로 바꾼다 */
	void replaceMembers(long id, Set<Long> storylineIds);

	/** 추적 씨앗을 통째로 바꾼다 */
	void replaceSeeds(long id, Set<Long> storylineIds);

	/** 헤드를 쓴다 — 잇기 결과 */
	void setHead(long id, long head);

	/** 구독 행을 둔다 — 이미 있으면 추적 시각을 {@code at} 으로 고친다 */
	void subscribe(UserId user, long id, Instant at);

	/** 구독 행을 지운다 — 없으면 아무것도 하지 않는다 */
	void unsubscribe(long id);

	/**
	 * 이 스토리라인 가운데 하나라도 추적 대상이나 추적 씨앗으로 가진 사용자 — 백그라운드 작업(따라가기) 전용.
	 * @return 사용자 ID 가 {@code afterUserId} 보다 큰 것을 ID 순으로 {@code limit} 명까지, 중복 없음
	 */
	List<UserId> usersHolding(Collection<Long> storylineIds, long afterUserId, int limit);

}
