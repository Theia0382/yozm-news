package net.yozmnews.evaluation.application;

import java.util.Objects;
import java.util.Optional;

import net.yozmnews.evaluation.EvaluationAdministration;
import net.yozmnews.evaluation.EvaluationOverview;
import net.yozmnews.evaluation.EvaluationPeriod;
import net.yozmnews.evaluation.EvaluationQueueCounts;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.domain.overview.OverviewTally;
import net.yozmnews.evaluation.domain.queue.EvaluationQueueRepository;
import net.yozmnews.evaluation.domain.result.EvaluationResultRepository;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공개 운영 API {@link EvaluationAdministration} 의 구현 — docs/specs/evaluation.md §4. 역할 애너테이션이 없다(두 역할 공통).
 * <p>
 * 대기 건수는 트랜잭션을 열지 않는다. 평가 현황은 읽기 전용 {@code REPEATABLE READ} 트랜잭션 하나에서 센다 — 체계 · 기준 · 이력 · 평가 행이 한
 * 스냅숏이고, 결과 저장소가 평가 행을 커서로 나눠 받는다.
 */
@Service
class EvaluationAdministrationService implements EvaluationAdministration {

	private final EvaluationQueueRepository queue;

	private final TagSetRepository tagSets;

	private final TagSetViews views;

	private final ThresholdRepository thresholds;

	private final EvaluationResultRepository results;

	private final TransactionTemplate snapshot;

	EvaluationAdministrationService(EvaluationQueueRepository queue, TagSetRepository tagSets, TagSetViews views,
			ThresholdRepository thresholds, EvaluationResultRepository results, PlatformTransactionManager transactions) {
		this.queue = queue;
		this.tagSets = tagSets;
		this.views = views;
		this.thresholds = thresholds;
		this.results = results;
		this.snapshot = new TransactionTemplate(transactions);
		this.snapshot.setReadOnly(true);
		this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
	}

	@Override
	public EvaluationQueueCounts queueCounts() {
		return this.queue.counts();
	}

	@Override
	public Optional<EvaluationOverview> overview(TagSetId version, EvaluationPeriod period) {
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(period, "period");
		return Objects.requireNonNull(this.snapshot.execute(status -> {
			if (!this.tagSets.existsById(version.value())) {
				return Optional.<EvaluationOverview>empty();
			}
			OverviewTally tally = new OverviewTally(this.views.of(version), this.thresholds.current(version),
					this.thresholds.changes(version), period);
			this.results.streamVersionPeriod(version, period.from(), period.to(), tally::add);
			return Optional.of(tally.overview());
		}));
	}

}
