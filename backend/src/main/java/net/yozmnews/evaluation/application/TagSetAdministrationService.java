package net.yozmnews.evaluation.application;

import java.math.BigDecimal;
import java.time.InstantSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetAdministration;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetVersionView;
import net.yozmnews.evaluation.TagThresholds;
import net.yozmnews.evaluation.ThresholdChangeResult;
import net.yozmnews.evaluation.ThresholdChangeView;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdDecision;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdRule;
import net.yozmnews.evaluation.domain.threshold.ThresholdValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공개 API {@link TagSetAdministration} 의 구현 — docs/specs/evaluation.md §4 · evaluation-R11 ~ evaluation-R13.
 * <p>
 * 변경은 한 트랜잭션에서 활성 버전 조회 → 지금 태그 기준 · 저장 기준 행 잠금 → 지난 저장 기준 최댓값 → 판정 → 쓰기다. 역할 애너테이션이 없다(두 역할
 * 공통). 버전 연산은 {@link TagSetVersioning} 에 맡긴다.
 */
@Service
class TagSetAdministrationService implements TagSetAdministration {

	private final TagSetRepository tagSets;

	private final ThresholdRepository thresholds;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	private final TagSetVersioning versioning;

	@Autowired
	TagSetAdministrationService(TagSetRepository tagSets, ThresholdRepository thresholds,
			TransactionTemplate transaction, TagSetVersioning versioning) {
		this(tagSets, thresholds, transaction, InstantSource.system(), versioning);
	}

	TagSetAdministrationService(TagSetRepository tagSets, ThresholdRepository thresholds,
			TransactionTemplate transaction, InstantSource clock, TagSetVersioning versioning) {
		this.tagSets = tagSets;
		this.thresholds = thresholds;
		this.transaction = transaction;
		this.clock = clock;
		this.versioning = versioning;
	}

	@Override
	public ThresholdChangeResult changeThresholds(MultiTagKind kind, BigDecimal tagThreshold,
			BigDecimal storeThreshold, ChangeActor actor) {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(tagThreshold, "tagThreshold");
		Objects.requireNonNull(storeThreshold, "storeThreshold");
		Objects.requireNonNull(actor, "actor");
		return Objects.requireNonNull(this.transaction.execute(status -> {
			TagSetId version = TagSetDirectoryService.active(this.tagSets).id();
			ThresholdValues current = this.thresholds.lockCurrent(version, kind)
				.orElseThrow(() -> new IllegalStateException("태그 기준 · 저장 기준 행이 없다: " + version + " " + kind));
			BigDecimal pastMax = this.thresholds.maxStoreThreshold(version, kind);
			return switch (ThresholdRule.decide(current, pastMax, tagThreshold, storeThreshold)) {
				case ThresholdDecision.OutOfRange outOfRange -> new ThresholdChangeResult.OutOfRange();
				case ThresholdDecision.Unchanged unchanged -> new ThresholdChangeResult.Unchanged(view(kind, current));
				case ThresholdDecision.Change change -> {
					this.thresholds.apply(version, kind, current, change.next(), actor, this.clock.instant());
					yield new ThresholdChangeResult.Changed(view(kind, current), view(kind, change.next()),
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
	public List<TagSetVersionView> versions() {
		return this.versioning.versions();
	}

	@Override
	public Optional<String> versionText(TagSetId id) {
		return this.versioning.versionText(Objects.requireNonNull(id, "id"));
	}

	private static TagThresholds view(MultiTagKind kind, ThresholdValues values) {
		return new TagThresholds(kind, values.tagThreshold(), values.storeThreshold());
	}

}
