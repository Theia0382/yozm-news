package net.yozmnews.tag.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.tag.EvaluationPeriod;
import net.yozmnews.tag.TagKeyLineage;
import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TagSetThresholds;
import net.yozmnews.tag.TagSetView;
import net.yozmnews.tag.TopicCoverage;
import net.yozmnews.tag.TopicCoverageResult;
import net.yozmnews.tag.domain.overview.CoverageTally;
import net.yozmnews.tag.domain.result.EvaluationResultRepository;
import net.yozmnews.tag.domain.result.TagJudgement;
import net.yozmnews.tag.domain.threshold.ThresholdRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공개 조회 {@link TopicCoverage} 의 구현 — docs/specs/tag.md §4. 역할 애너테이션이 없다(두 역할 공통).
 * <p>
 * 읽기 전용 {@code REPEATABLE READ} 트랜잭션 하나에서 센다 — 활성 버전 · 체계 · 기준 · 계보 · 평가 행이 한 스냅숏이라 계보 표의 기준 버전이
 * 읽은 활성 버전과 같다. 평가에 나온 버전마다 체계 · 지금 기준 · 계보 표를 한 번씩 읽는다(캐시는 호출 하나 안).
 */
@Service
class TopicCoverageService implements TopicCoverage {

	private final TagSetDirectory directory;

	private final TagSetViews views;

	private final ThresholdRepository thresholds;

	private final TagKeyLineage lineage;

	private final EvaluationResultRepository results;

	private final TransactionTemplate snapshot;

	TopicCoverageService(TagSetDirectory directory, TagSetViews views, ThresholdRepository thresholds,
			TagKeyLineage lineage, EvaluationResultRepository results, PlatformTransactionManager transactions) {
		this.directory = directory;
		this.views = views;
		this.thresholds = thresholds;
		this.lineage = lineage;
		this.results = results;
		this.snapshot = new TransactionTemplate(transactions);
		this.snapshot.setReadOnly(true);
		this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
	}

	@Override
	public TopicCoverageResult count(EvaluationPeriod period, Set<String> topics) {
		Objects.requireNonNull(period, "period");
		Set<String> chosen = Set.copyOf(topics);
		return Objects.requireNonNull(this.snapshot.execute(status -> {
			TagSetView active = this.directory.activeTagSet();
			Set<String> unknown = CoverageTally.unknownTopics(active, chosen);
			if (!unknown.isEmpty()) {
				return new TopicCoverageResult.UnknownTopics(unknown);
			}
			CoverageTally tally = new CoverageTally(active, chosen, period);
			Map<TagSetId, Version> versions = new HashMap<>();
			this.results.streamFirstEvaluatedIn(period.from(), period.to(), evaluation -> {
				Version version = versions.computeIfAbsent(evaluation.tagSetId(), this::version);
				tally.add(TagJudgement.judge(evaluation.tags(), version.tagSet(), version.thresholds()),
						version.mapping());
			});
			return new TopicCoverageResult.Counted(tally.view());
		}));
	}

	private Version version(TagSetId id) {
		return new Version(this.views.of(id), this.thresholds.current(id), this.lineage.toActive(id));
	}

	/** 평가에 나온 버전 하나의 체계 · 지금 기준 · 활성 버전으로의 계보 표 */
	private record Version(TagSetView tagSet, TagSetThresholds thresholds, TagKeyMapping mapping) {
	}

}
