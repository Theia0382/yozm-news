package net.yozmnews.evaluation.domain.queue;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EvaluationQueueCounts;

/**
 * 평가 대기 저장소 — docs/specs/evaluation.md §4
 */
public interface EvaluationQueueRepository {

	/** 대기에 넣는다 — 평가 가능 시각은 {@link EvaluationSchedule#eligibleAfterEnqueue}. 이미 있으면 아무것도 바꾸지 않는다 */
	void enqueue(ArticleId articleId, Instant now);

	/**
	 * 재평가 대기에 넣는다 — 없으면 넣고, 실패로 남은 행은 실패 · 시도 수 · 마지막 문구를 지우고 되돌린다. 평가 가능 시각은
	 * {@link EvaluationSchedule#eligibleAfterRequeue}. 대기 중 · 점유 중인 행은 그대로다(evaluation-R25). 같은 기사 ID 가 여러 번
	 * 있어도 한 번만 센다.
	 * @return 넣거나 되돌린 행 수
	 */
	int requeue(Collection<ArticleId> articleIds, Instant now);

	/**
	 * 지금 평가할 수 있는 대기 행을 잠근다 — 실패로 남지 않았고, 평가 가능 시각이 지났고, 점유가 없거나 만료된 행. 평가 가능 시각, 기사 ID 순.
	 * 다른 트랜잭션이 잠근 행은 건너뛴다. 트랜잭션 안에서 부른다.
	 */
	List<PendingEvaluation> lockDue(Instant now, int limit);

	/** {@link #lockDue} 로 잠근 행을 고쳐 쓴다 */
	void saveLocked(PendingEvaluation next);

	/**
	 * 점유 토큰이 맞을 때만 고쳐 쓴다.
	 * @return 고쳐 썼으면 true
	 */
	boolean replaceIfLeased(UUID token, PendingEvaluation next);

	/**
	 * 점유 토큰이 맞을 때만 지운다.
	 * @return 지웠으면 true
	 */
	boolean deleteIfLeased(ArticleId articleId, UUID token);

	/** 기사들의 대기 행을 지운다 — 지운 기사를 따른다 */
	void delete(Collection<ArticleId> articleIds);

	/** 대기 · 실패 건수 */
	EvaluationQueueCounts counts();

}
