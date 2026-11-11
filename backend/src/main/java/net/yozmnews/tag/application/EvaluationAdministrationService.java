package net.yozmnews.tag.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.tag.EvaluationAdministration;
import net.yozmnews.tag.EvaluationOverview;
import net.yozmnews.tag.EvaluationPeriod;
import net.yozmnews.tag.EvaluationQueueCounts;
import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.ThresholdChangeView;
import net.yozmnews.tag.domain.overview.OverviewTally;
import net.yozmnews.tag.domain.queue.EvaluationQueueRepository;
import net.yozmnews.tag.domain.result.EvaluationResultRepository;
import net.yozmnews.tag.domain.tagset.TagSetRepository;
import net.yozmnews.tag.domain.threshold.ThresholdRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공개 운영 API {@link EvaluationAdministration} 의 구현 — docs/specs/tag.md §4. 역할 애너테이션이 없다(두 역할 공통).
 * <p>
 * 대기 건수는 트랜잭션을 열지 않는다. 평가 현황은 읽기 전용 {@code REPEATABLE READ} 트랜잭션 하나에서 센다 — 체계 · 기준 · 이력 · 끝난 채움 ·
 * 평가 행이 한 스냅숏이고, 결과 저장소가 평가 행을 커서로 나눠 받는다.
 */
@Service
class EvaluationAdministrationService implements EvaluationAdministration {

	private final EvaluationQueueRepository queue;

	private final TagSetRepository tagSets;

	private final TagSetViews views;

	private final ThresholdRepository thresholds;

	private final EvaluationResultRepository results;

	private final StoreFloorsReader floors;

	private final JudgedSamples samples;

	private final TransactionTemplate snapshot;

	EvaluationAdministrationService(EvaluationQueueRepository queue, TagSetRepository tagSets, TagSetViews views,
			ThresholdRepository thresholds, EvaluationResultRepository results, StoreFloorsReader floors,
			JudgedSamples samples, PlatformTransactionManager transactions) {
		this.queue = queue;
		this.tagSets = tagSets;
		this.views = views;
		this.thresholds = thresholds;
		this.results = results;
		this.floors = floors;
		this.samples = samples;
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
			List<ThresholdChangeView> changes = this.thresholds.changes(version);
			OverviewTally tally = new OverviewTally(this.views.of(version), this.thresholds.current(version),
					this.floors.floors(version, MultiTagKind.TOPIC, changes),
					this.floors.floors(version, MultiTagKind.REGION, changes),
					period);
			this.samples.stream(rows -> this.results.streamVersionPeriod(version, period.from(), period.to(), rows),
					tally::add);
			return Optional.of(tally.overview());
		}));
	}

}
