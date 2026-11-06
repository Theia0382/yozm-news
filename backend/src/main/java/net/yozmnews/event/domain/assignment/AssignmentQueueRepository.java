package net.yozmnews.event.domain.assignment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import net.yozmnews.article.ArticleId;

/**
 * 배정 대기 저장소 — docs/specs/event.md §4
 */
public interface AssignmentQueueRepository {

	/** 대기에 넣는다 — 판정 가능 시각은 {@code now}. 이미 있으면 아무것도 바꾸지 않는다 */
	void enqueue(ArticleId articleId, Instant now);

	/**
	 * 판정 가능 시각이 지났고(≤ {@code now}) 점유가 없거나 만료된(만료 시각 ≤ {@code now}) 행을 판정 가능 시각 · 기사 ID 순으로
	 * {@code limit} 개까지 잠근다. 다른 트랜잭션이 잠근 행은 건너뛴다. 트랜잭션 안에서 부른다.
	 */
	List<PendingAssignment> lockDue(Instant now, int limit);

	/** {@link #lockDue} 로 잠근 행을 고쳐 쓴다 */
	void saveLocked(PendingAssignment next);

	/**
	 * 점유 토큰이 맞을 때만 지운다.
	 * @return 지웠으면 true
	 */
	boolean deleteIfLeased(ArticleId articleId, UUID token);

	/**
	 * 점유 토큰이 맞을 때만 점유를 푼다 — 시도 수 · 판정 가능 시각은 그대로.
	 * @return 풀었으면 true
	 */
	boolean releaseIfLeased(ArticleId articleId, UUID token);

	/**
	 * 점유 토큰이 맞을 때만 일시 실패를 적는다 — 시도 수 · 판정 가능 시각을 쓰고 점유를 푼다.
	 * @return 적었으면 true
	 */
	boolean retryIfLeased(ArticleId articleId, UUID token, int attempts, Instant availableAt);

	/** 기사들의 대기 행을 지운다 — 지운 기사 따르기 */
	void delete(Collection<ArticleId> articleIds);

}
