package net.yozmnews.storyline.domain.relation;

import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * 관계 대기 저장소 — docs/specs/storyline.md §4 · storyline-R26 · storyline-R31.
 */
public interface RelationQueueRepository {

	/** 대기에 넣는다 — 이미 있으면 다시 알림 수를 하나 올린다(점유 · 실패 수는 그대로) */
	void enqueue(long storylineId, Instant now);

	/**
	 * 점유가 없거나 만료된(만료 시각 ≤ {@code now}) 행을 스토리라인 ID 순으로 {@code limit} 개까지 잠근다. 다른 트랜잭션이 잠근 행은
	 * 건너뛴다. 트랜잭션 안에서 부른다.
	 */
	List<PendingStoryline> lockDue(Instant now, int limit);

	/** {@link #lockDue} 로 잠근 행의 점유를 쓴다 */
	void saveLocked(PendingStoryline next);

	/**
	 * 점유 토큰과 다시 알림 수가 모두 맞을 때만 지운다.
	 * @return 지웠으면 true
	 */
	boolean deleteIfUnchanged(long storylineId, UUID token, int notifications);

	/**
	 * 점유 토큰이 맞을 때만 점유를 푼다.
	 * @return 풀었으면 true
	 */
	boolean releaseIfLeased(long storylineId, UUID token);

	/**
	 * 점유 토큰이 맞을 때만 실패 수를 하나 올리고 점유를 푼다.
	 * @return 올린 뒤의 실패 수 — 토큰이 맞지 않으면 빈 값
	 */
	OptionalInt recordFailureIfLeased(long storylineId, UUID token);

	/** 대기 행을 지운다 */
	void delete(long storylineId);

}
