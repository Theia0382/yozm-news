package net.yozmnews.evaluation.application;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.evaluation.TagBackfillAdministration;
import net.yozmnews.evaluation.TagBackfillRequestResult;
import net.yozmnews.evaluation.TagBackfillReviewResult;
import net.yozmnews.evaluation.TagBackfillRunView;
import net.yozmnews.evaluation.TagBackfillVersionCount;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.domain.backfill.BackfillSchedule;
import net.yozmnews.evaluation.domain.backfill.BackfillVersionPlan;
import net.yozmnews.evaluation.domain.backfill.TagBackfill;
import net.yozmnews.evaluation.domain.backfill.TagBackfillRepository;
import net.yozmnews.evaluation.domain.result.EvaluationResultRepository;
import net.yozmnews.evaluation.domain.tagset.TagSet;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공개 운영 API {@link TagBackfillAdministration} 의 구현 — docs/specs/evaluation.md §4. 역할 애너테이션이 없다(두 역할 공통).
 * <p>
 * 덮는 범위는 {@code [지금 − 일수 × 24시간, 지금)} 이다. 요청은 한 트랜잭션에서 표를 활성 전환과 충돌하는 모드로 잠근 뒤 모든 버전의 지금 저장
 * 기준을 채울 기준으로 기록한다 — 실행은 작업 역할의 {@link TagBackfillRound} 가 한다.
 */
@Service
class TagBackfillAdministrationService implements TagBackfillAdministration {

	private final TagSetRepository tagSets;

	private final ThresholdRepository thresholds;

	private final EvaluationResultRepository results;

	private final TagBackfillRepository backfills;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	TagBackfillAdministrationService(TagSetRepository tagSets, ThresholdRepository thresholds,
			EvaluationResultRepository results, TagBackfillRepository backfills, TransactionTemplate transaction) {
		this(tagSets, thresholds, results, backfills, transaction, InstantSource.system());
	}

	TagBackfillAdministrationService(TagSetRepository tagSets, ThresholdRepository thresholds,
			EvaluationResultRepository results, TagBackfillRepository backfills, TransactionTemplate transaction,
			InstantSource clock) {
		this.tagSets = tagSets;
		this.thresholds = thresholds;
		this.results = results;
		this.backfills = backfills;
		this.transaction = transaction;
		this.clock = clock;
	}

	@Override
	public TagBackfillReviewResult review(int days) {
		if (outOfRange(days)) {
			return new TagBackfillReviewResult.DaysOutOfRange();
		}
		Instant to = this.clock.instant();
		Instant from = from(to, days);
		Map<TagSetId, Long> counts = this.results.countByVersion(from, to);
		List<TagBackfillVersionCount> versions = new ArrayList<>();
		for (TagSet version : this.tagSets.findAllByOrderByNumberDesc().reversed()) {
			Long evaluations = counts.get(version.id());
			if (evaluations != null) {
				versions.add(new TagBackfillVersionCount(version.id(), version.number(), evaluations));
			}
		}
		return new TagBackfillReviewResult.Ready(from, to,
				versions.stream().mapToLong(TagBackfillVersionCount::evaluations).sum(), versions);
	}

	@Override
	public TagBackfillRequestResult request(int days) {
		if (outOfRange(days)) {
			return new TagBackfillRequestResult.DaysOutOfRange();
		}
		return Objects.requireNonNull(this.transaction.execute(status -> {
			// 활성 전환과 줄을 선다 — 버전 목록과 그 지금 기준이 한 순간의 것이다
			this.tagSets.lockAgainstChange();
			Instant to = this.clock.instant();
			Instant from = from(to, days);
			Map<TagSetId, Long> counts = this.results.countByVersion(from, to);
			List<BackfillVersionPlan> plans = new ArrayList<>();
			for (TagSet version : this.tagSets.findAllByOrderByNumberDesc().reversed()) {
				TagSetThresholds current = this.thresholds.current(version.id());
				plans.add(new BackfillVersionPlan(version.id(), current.topic().storeThreshold(),
						current.region().storeThreshold(), counts.getOrDefault(version.id(), 0L)));
			}
			// 열린 채움이 넣기와 읽기 사이에 끝나면 읽기가 비므로 넣기를 한 번 더 한다
			for (int attempt = 0; attempt < 2; attempt++) {
				Optional<Long> inserted = this.backfills.insertIfNoneOpen(days, from, to,
						BackfillSchedule.eligibleAt(to), plans);
				if (inserted.isPresent()) {
					return new TagBackfillRequestResult.Requested(run(inserted.get()));
				}
				Optional<TagBackfill> open = this.backfills.findOpen();
				if (open.isPresent()) {
					return new TagBackfillRequestResult.AlreadyOpen(run(open.get().id()));
				}
			}
			throw new IllegalStateException("채움을 넣지도 열린 채움을 읽지도 못했다");
		}));
	}

	@Override
	public List<TagBackfillRunView> runs() {
		return this.backfills.runs();
	}

	private TagBackfillRunView run(long id) {
		return this.backfills.run(id).orElseThrow(() -> new IllegalStateException("채움이 없다: " + id));
	}

	private static boolean outOfRange(int days) {
		return days < 1 || days > MAX_DAYS;
	}

	private static Instant from(Instant to, int days) {
		return to.minus(Duration.ofDays(days));
	}

}
