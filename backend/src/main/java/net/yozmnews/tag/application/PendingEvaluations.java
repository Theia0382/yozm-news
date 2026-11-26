package net.yozmnews.tag.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.yozmnews.tag.domain.queue.EvaluationQueueRepository;
import net.yozmnews.tag.domain.queue.EvaluationSchedule;
import net.yozmnews.tag.domain.queue.PendingEvaluation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 지금 평가할 대기의 점유 — docs/specs/tag.md §4 · tag-R19.
 * <p>
 * 한 트랜잭션에서 평가할 수 있는 대기 행을 잠그고 점유를 써 커밋한다. 다른 뭉치 · 인스턴스가 잡은 행은 건너뛴다.
 */
@Service
class PendingEvaluations {

	private final EvaluationQueueRepository queue;

	private final TransactionTemplate transaction;

	PendingEvaluations(EvaluationQueueRepository queue, TransactionTemplate transaction) {
		this.queue = queue;
		this.transaction = transaction;
	}

	/**
	 * 지금 평가할 대기를 점유한다.
	 * @param expiredBy 임대 만료 시각의 상한 — 부르는 쪽이 시계가 넘긴 시간을 뺀 경과로 정한다({@code AppClock#expiredBy})
	 * @return 점유한 대기 — 평가 가능 시각, 기사 ID 순
	 */
	List<PendingEvaluation> claimDue(Instant now, Instant expiredBy) {
		return this.transaction.execute(status -> {
			List<PendingEvaluation> claimed = new ArrayList<>();
			for (PendingEvaluation due : this.queue.lockDue(now, expiredBy, EvaluationSchedule.BATCH_SIZE)) {
				PendingEvaluation lease = due.claim(UUID.randomUUID(), now);
				this.queue.saveLocked(lease);
				claimed.add(lease);
			}
			return claimed;
		});
	}

}
