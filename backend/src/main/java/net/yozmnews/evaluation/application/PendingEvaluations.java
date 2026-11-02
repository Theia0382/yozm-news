package net.yozmnews.evaluation.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.yozmnews.evaluation.domain.queue.EvaluationQueueRepository;
import net.yozmnews.evaluation.domain.queue.PendingEvaluation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 지금 평가할 대기의 점유 — docs/specs/evaluation.md §4 · evaluation-R19.
 * <p>
 * 한 트랜잭션에서 평가할 수 있는 대기 행을 잠그고 점유를 써 커밋한다. 다른 회차 · 인스턴스가 잡은 행은 건너뛴다.
 */
@Service
class PendingEvaluations {

	/** 한 회차에 점유하는 최대 기사 수 — 측정 전 목표다 */
	static final int CLAIM_BATCH = 50;

	private final EvaluationQueueRepository queue;

	private final TransactionTemplate transaction;

	PendingEvaluations(EvaluationQueueRepository queue, TransactionTemplate transaction) {
		this.queue = queue;
		this.transaction = transaction;
	}

	/**
	 * 지금 평가할 대기를 점유한다.
	 * @return 점유한 대기 — 평가 가능 시각, 기사 ID 순
	 */
	List<PendingEvaluation> claimDue(Instant now) {
		return this.transaction.execute(status -> {
			List<PendingEvaluation> claimed = new ArrayList<>();
			for (PendingEvaluation due : this.queue.lockDue(now, CLAIM_BATCH)) {
				PendingEvaluation lease = due.claim(UUID.randomUUID(), now);
				this.queue.saveLocked(lease);
				claimed.add(lease);
			}
			return claimed;
		});
	}

}
