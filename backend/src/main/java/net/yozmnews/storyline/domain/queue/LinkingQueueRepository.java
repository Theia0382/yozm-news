package net.yozmnews.storyline.domain.queue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * 잇기 대기 저장소 — docs/specs/storyline.md §4 · storyline-R2 · storyline-R3 · storyline-R4.
 */
public interface LinkingQueueRepository {

	/**
	 * 대기에 넣는다 — 이미 있으면 다시 알림 수를 하나 올리고 순서 키를 새 값으로 바꾼다(점유 · 실패 수 · 일시 실패 수 · 판정 가능 시각은
	 * 그대로)
	 */
	void enqueue(long eventId, @Nullable Instant orderAt, Instant now);

	/**
	 * 여럿을 같은 넣기로 넣는다 — 사건 ID 순.
	 * @param orderAtByEvent 사건 ID → 순서 키
	 * @return 새로 넣은 행 수 — 이미 있던 행은 다시 알림으로 세고 이 수에 들지 않는다
	 */
	int enqueueAll(Map<Long, Instant> orderAtByEvent, Instant now);

	/**
	 * 점유가 없거나 만료된(만료 시각 ≤ {@code expiredBy}) 행 가운데 판정 가능 시각이 없거나 지난(≤ {@code now}) 행을 순서 키(없음 먼저) ·
	 * 사건 ID 순으로 {@code limit} 개까지 잠근다. 다른 트랜잭션이 잠근 행은 건너뛴다. 트랜잭션 안에서 부른다.
	 * @param expiredBy 만료 시각의 상한 — 부르는 쪽이 시계가 넘긴 시간을 뺀 경과로 정한다({@code AppClock#expiredBy})
	 */
	List<PendingEvent> lockDue(Instant now, Instant expiredBy, int limit);

	/** {@link #lockDue} 로 잠근 행의 점유를 쓴다 */
	void saveLocked(PendingEvent next);

	/**
	 * 점유 토큰과 다시 알림 수가 모두 맞을 때만 지운다.
	 * @return 지웠으면 true
	 */
	boolean deleteIfUnchanged(long eventId, UUID token, int notifications);

	/**
	 * 점유 토큰이 맞을 때만 점유를 푼다.
	 * @return 풀었으면 true
	 */
	boolean releaseIfLeased(long eventId, UUID token);

	/**
	 * 점유 토큰이 맞을 때만 실패 수(판정이 예상하지 못한 예외로 실패한 수)를 하나 올리고 점유를 푼다.
	 * @return 올린 뒤의 실패 수 — 토큰이 맞지 않으면 빈 값
	 */
	OptionalInt recordFailureIfLeased(long eventId, UUID token);

	/**
	 * 점유 토큰이 맞을 때만 일시 실패 수(스토리라인 소속 판정이 평가기 일시 실패로 끝난 수)를 하나 올리고 점유를 푼다. 실패 수는 그대로다.
	 * @return 올린 뒤의 일시 실패 수 — 토큰이 맞지 않으면 빈 값
	 */
	OptionalInt recordTransientFailureIfLeased(long eventId, UUID token);

	/**
	 * 판정 가능 시각을 정한다 — 평가기 일시 실패 뒤 미룰 때, 같은 트랜잭션에서 {@link #recordTransientFailureIfLeased} 로 일시 실패를
	 * 센 뒤에 부른다. 행이 지워진 뒤에 다시 넣으면 판정 가능 시각이 없다
	 */
	void defer(long eventId, Instant retryAt);

	/** 대기 행을 지운다 */
	void delete(long eventId);

}
