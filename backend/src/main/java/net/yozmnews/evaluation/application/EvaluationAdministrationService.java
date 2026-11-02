package net.yozmnews.evaluation.application;

import net.yozmnews.evaluation.EvaluationAdministration;
import net.yozmnews.evaluation.EvaluationQueueCounts;
import net.yozmnews.evaluation.domain.queue.EvaluationQueueRepository;
import org.springframework.stereotype.Service;

/**
 * 공개 운영 API {@link EvaluationAdministration} 의 구현 — docs/specs/evaluation.md §4. 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class EvaluationAdministrationService implements EvaluationAdministration {

	private final EvaluationQueueRepository queue;

	EvaluationAdministrationService(EvaluationQueueRepository queue) {
		this.queue = queue;
	}

	@Override
	public EvaluationQueueCounts queueCounts() {
		return this.queue.counts();
	}

}
