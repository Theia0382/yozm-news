package net.yozmnews.evaluation.application;

import java.math.BigDecimal;
import java.time.InstantSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.EvaluationPeriod;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetActivationResult;
import net.yozmnews.evaluation.TagSetAdministration;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetReviewResult;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TagSetVersionView;
import net.yozmnews.evaluation.TagThresholds;
import net.yozmnews.evaluation.ThresholdChangeResult;
import net.yozmnews.evaluation.ThresholdChangeView;
import net.yozmnews.evaluation.ThresholdKindStatus;
import net.yozmnews.evaluation.ThresholdPreviewResult;
import net.yozmnews.evaluation.ThresholdStatusView;
import net.yozmnews.evaluation.domain.tagset.TagSet;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.threshold.StoreFloors;
import net.yozmnews.evaluation.domain.threshold.ThresholdDecision;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdRule;
import net.yozmnews.evaluation.domain.threshold.ThresholdStanding;
import net.yozmnews.evaluation.domain.threshold.ThresholdValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공개 API {@link TagSetAdministration} 의 구현 — docs/specs/evaluation.md §4 · evaluation-R11 ~ evaluation-R13.
 * <p>
 * 변경은 한 트랜잭션에서 표 잠금(활성 전환과 줄 서기) → 활성 버전 조회 → 지금 태그 기준 · 저장 기준 행 잠금 → 지난 저장 기준 최댓값(이력과
 * 끝난 채움의 저장 바닥 — {@link StoreFloors}) → 판정 → 쓰기다. 역할 애너테이션이 없다(두 역할 공통). 버전 연산은 {@link TagSetVersioning},
 * 기준 미리보기는 {@link ThresholdPreviews} 에 맡긴다.
 */
@Service
class TagSetAdministrationService implements TagSetAdministration {

	private final TagSetRepository tagSets;

	private final ThresholdRepository thresholds;

	private final TransactionTemplate transaction;

	/** 기준 상태 읽기 — 읽기 전용 {@code REPEATABLE READ}: 지금 값 · 이력 · 끝난 채움의 덮음을 한 스냅숏에서 읽는다 */
	private final TransactionTemplate readOnly;

	private final InstantSource clock;

	private final TagSetVersioning versioning;

	private final StoreFloorsReader floors;

	private final ThresholdPreviews previews;

	@Autowired
	TagSetAdministrationService(TagSetRepository tagSets, ThresholdRepository thresholds,
			TransactionTemplate transaction, TagSetVersioning versioning, StoreFloorsReader floors,
			ThresholdPreviews previews) {
		this(tagSets, thresholds, transaction, InstantSource.system(), versioning, floors, previews);
	}

	TagSetAdministrationService(TagSetRepository tagSets, ThresholdRepository thresholds,
			TransactionTemplate transaction, InstantSource clock, TagSetVersioning versioning,
			StoreFloorsReader floors, ThresholdPreviews previews) {
		this.tagSets = tagSets;
		this.thresholds = thresholds;
		this.transaction = transaction;
		this.readOnly = new TransactionTemplate(Objects.requireNonNull(transaction.getTransactionManager(),
				"transactionManager"));
		this.readOnly.setReadOnly(true);
		this.readOnly.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
		this.clock = clock;
		this.versioning = versioning;
		this.floors = floors;
		this.previews = previews;
	}

	@Override
	public ThresholdChangeResult changeThresholds(MultiTagKind kind, BigDecimal tagThreshold,
			BigDecimal storeThreshold, ChangeActor actor) {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(tagThreshold, "tagThreshold");
		Objects.requireNonNull(storeThreshold, "storeThreshold");
		Objects.requireNonNull(actor, "actor");
		return Objects.requireNonNull(this.transaction.execute(status -> {
			// 활성 전환과 줄을 선다 — 활성을 읽은 뒤 커밋 전에 전환이 끼면 변경이 방금 은퇴한 버전에 들어간다(evaluation-R11)
			this.tagSets.lockAgainstChange();
			TagSetId version = TagSetDirectoryService.active(this.tagSets).id();
			ThresholdValues current = this.thresholds.lockCurrent(version, kind)
				.orElseThrow(() -> new IllegalStateException("태그 기준 · 저장 기준 행이 없다: " + version + " " + kind));
			BigDecimal pastMax = this.floors.floors(version, kind, this.thresholds.changes(version)).pastMax();
			return switch (ThresholdRule.decide(current, pastMax, tagThreshold, storeThreshold)) {
				case ThresholdDecision.OutOfRange outOfRange -> new ThresholdChangeResult.OutOfRange();
				case ThresholdDecision.Unchanged unchanged ->
					new ThresholdChangeResult.Unchanged(version, current.toView(kind));
				case ThresholdDecision.Change change -> {
					this.thresholds.apply(version, kind, current, change.next(), actor, this.clock.instant());
					yield new ThresholdChangeResult.Changed(version, current.toView(kind), change.next().toView(kind),
							change.warnings());
				}
			};
		}));
	}

	@Override
	public List<ThresholdChangeView> thresholdChanges() {
		return this.thresholds.changes(TagSetDirectoryService.active(this.tagSets).id());
	}

	@Override
	public ThresholdStatusView thresholdStatus() {
		return Objects.requireNonNull(this.readOnly.execute(status -> {
			TagSet active = TagSetDirectoryService.active(this.tagSets);
			TagSetId version = active.id();
			TagSetThresholds current = this.thresholds.current(version);
			List<ThresholdChangeView> changes = this.thresholds.changes(version);
			return new ThresholdStatusView(version, active.number(), kindStatus(current.topic(), version, changes),
					kindStatus(current.region(), version, changes), changes);
		}));
	}

	@Override
	public ThresholdPreviewResult previewThresholds(MultiTagKind kind, BigDecimal tagThreshold,
			BigDecimal storeThreshold, EvaluationPeriod period) {
		return this.previews.preview(kind, tagThreshold, storeThreshold, period);
	}

	/** 한 태그 종류의 상태 — 판정은 {@link ThresholdStanding} */
	private ThresholdKindStatus kindStatus(TagThresholds current, TagSetId version, List<ThresholdChangeView> changes) {
		StoreFloors floors = this.floors.floors(version, current.kind(), changes);
		ThresholdValues values = new ThresholdValues(current.tagThreshold(), current.storeThreshold());
		return new ThresholdKindStatus(current, floors.pastMax(), ThresholdStanding.of(values, floors));
	}

	@Override
	public List<TagSetVersionView> versions() {
		return this.versioning.versions();
	}

	@Override
	public Optional<String> versionText(TagSetId id) {
		return this.versioning.versionText(Objects.requireNonNull(id, "id"));
	}

	@Override
	public TagSetReviewResult review(String text, int recentDays) {
		return this.versioning.review(text, recentDays);
	}

	@Override
	public TagSetActivationResult activate(String text, int recentDays, ChangeActor actor) {
		return this.versioning.activate(text, recentDays, actor);
	}

}
