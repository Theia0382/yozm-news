package net.yozmnews.event.domain.intake;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import net.yozmnews.article.ArticleId;

/**
 * 사건 묶기 대기 저장소 — docs/specs/event.md §4
 */
public interface IntakeQueueRepository {

	/** 대기에 넣는다 — 이미 있으면 아무것도 바꾸지 않는다 */
	void enqueue(ArticleId articleId, Instant now);

	/**
	 * 배정이 없는 기사만 대기에 넣는다 — 이미 대기에 있으면 그대로. 운영자의 채우기가 쓴다.
	 * @return 새로 넣은 행 수
	 */
	int enqueueUnassigned(Collection<ArticleId> articleIds, Instant now);

	/**
	 * 점유가 없거나 만료된(만료 시각 ≤ {@code now}) 행을 넣은 시각 · 기사 ID 순으로 {@code limit} 개까지 잠근다. 다른 트랜잭션이 잠근 행은
	 * 건너뛴다. 트랜잭션 안에서 부른다.
	 */
	List<PendingArticle> lockDue(Instant now, int limit);

	/** {@link #lockDue} 로 잠근 행을 고쳐 쓴다 */
	void saveLocked(PendingArticle next);

	/**
	 * 점유 토큰이 맞을 때만 지운다.
	 * @return 지웠으면 true
	 */
	boolean deleteIfLeased(ArticleId articleId, UUID token);

	/**
	 * 점유 토큰이 맞을 때만 점유를 푼다.
	 * @return 풀었으면 true
	 */
	boolean releaseIfLeased(ArticleId articleId, UUID token);

	/**
	 * 점유 토큰이 맞을 때만 거부 수를 하나 올리고 점유를 푼다. 트랜잭션 안에서 부른다.
	 * @return 올린 뒤의 거부 수 — 토큰이 맞지 않으면 빈 값
	 */
	OptionalInt recordRejectionIfLeased(ArticleId articleId, UUID token);

	/** 기사들의 대기 행을 지운다 — 지운 기사 따르기 */
	void delete(Collection<ArticleId> articleIds);

}
