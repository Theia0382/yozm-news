package net.yozmnews.evaluation.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import net.yozmnews.evaluation.EvaluationOverview;
import net.yozmnews.evaluation.EvaluationPeriod;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TagThresholds;
import net.yozmnews.evaluation.ThresholdChangeView;
import net.yozmnews.evaluation.ThresholdPreviewResult;
import net.yozmnews.evaluation.domain.result.EvaluationResultRepository;
import net.yozmnews.evaluation.domain.review.ThresholdImpactTally;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.threshold.StoreFloors;
import net.yozmnews.evaluation.domain.threshold.ThresholdDecision;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdRule;
import net.yozmnews.evaluation.domain.threshold.ThresholdValues;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기준 미리보기 — docs/specs/evaluation.md §4 · evaluation-R35. {@code TagSetAdministrationService} 가 맡긴다. 역할 애너테이션이 없다(두
 * 역할 공통).
 * <p>
 * 읽기 전용 {@code REPEATABLE READ} 트랜잭션 하나에서 활성 버전 · 지금 기준 · 이력 · 끝난 채움의 덮음 · 평가 행을 읽는다 — 잠그지 않고 아무것도 쓰지 않는다. 범위 ·
 * 같은 값 · 경고는 변경과 같은 판정({@link ThresholdRule})이다.
 */
@Component
class ThresholdPreviews {

	private final TagSetRepository tagSets;

	private final TagSetViews views;

	private final ThresholdRepository thresholds;

	private final EvaluationResultRepository results;

	private final StoreFloorsReader floors;

	private final JudgedSamples samples;

	private final TransactionTemplate snapshot;

	ThresholdPreviews(TagSetRepository tagSets, TagSetViews views, ThresholdRepository thresholds,
			EvaluationResultRepository results, StoreFloorsReader floors, JudgedSamples samples,
			PlatformTransactionManager transactions) {
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

	/**
	 * @throws IllegalStateException 활성 버전이 없을 때
	 */
	ThresholdPreviewResult preview(MultiTagKind kind, BigDecimal tagThreshold, BigDecimal storeThreshold,
			EvaluationPeriod period) {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(tagThreshold, "tagThreshold");
		Objects.requireNonNull(storeThreshold, "storeThreshold");
		Objects.requireNonNull(period, "period");
		return Objects.requireNonNull(this.snapshot.execute(status -> {
			TagSetId version = TagSetDirectoryService.active(this.tagSets).id();
			TagSetThresholds current = this.thresholds.current(version);
			TagThresholds currentKind = switch (kind) {
				case TOPIC -> current.topic();
				case REGION -> current.region();
			};
			ThresholdValues values = new ThresholdValues(currentKind.tagThreshold(), currentKind.storeThreshold());
			List<ThresholdChangeView> changes = this.thresholds.changes(version);
			StoreFloors topicFloors = this.floors.floors(version, MultiTagKind.TOPIC, changes);
			StoreFloors regionFloors = this.floors.floors(version, MultiTagKind.REGION, changes);
			BigDecimal pastMax = switch (kind) {
				case TOPIC -> topicFloors.pastMax();
				case REGION -> regionFloors.pastMax();
			};
			return switch (ThresholdRule.decide(values, pastMax, tagThreshold, storeThreshold)) {
				case ThresholdDecision.OutOfRange outOfRange -> new ThresholdPreviewResult.OutOfRange();
				case ThresholdDecision.Unchanged unchanged ->
					new ThresholdPreviewResult.Unchanged(version, currentKind);
				case ThresholdDecision.Change change -> {
					TagThresholds proposedKind = change.next().toView(kind);
					TagSetThresholds proposed = switch (kind) {
						case TOPIC -> new TagSetThresholds(version, proposedKind, current.region());
						case REGION -> new TagSetThresholds(version, current.topic(), proposedKind);
					};
					ThresholdImpactTally tally = new ThresholdImpactTally(this.views.of(version), current, proposed,
							kind, topicFloors, regionFloors, period);
					this.samples.stream(
							rows -> this.results.streamVersionPeriod(version, period.from(), period.to(), rows), tally::add);
					EvaluationOverview before = tally.before();
					yield new ThresholdPreviewResult.Previewed(version, currentKind, proposedKind, change.warnings(),
							before.evaluations(), tally.changed(), before, tally.after());
				}
			};
		}));
	}

}
