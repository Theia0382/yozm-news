package net.yozmnews.evaluation.application;

import java.time.InstantSource;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagBackfillRunView;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.TagThresholds;
import net.yozmnews.evaluation.domain.backfill.BackfillCounts;
import net.yozmnews.evaluation.domain.backfill.BackfillSchedule;
import net.yozmnews.evaluation.domain.backfill.BackfillVersionPlan;
import net.yozmnews.evaluation.domain.backfill.TagBackfill;
import net.yozmnews.evaluation.domain.backfill.TagBackfillRepository;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.Evaluation;
import net.yozmnews.evaluation.domain.evaluator.RereadFailure;
import net.yozmnews.evaluation.domain.evaluator.RereadResult;
import net.yozmnews.evaluation.domain.result.EvaluationRecord;
import net.yozmnews.evaluation.domain.result.EvaluationResultRepository;
import net.yozmnews.evaluation.domain.result.ResultCheck;
import net.yozmnews.evaluation.domain.result.StoredResponse;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 빠진 태그 채움 회차 하나 — docs/specs/evaluation.md §4.
 * <p>
 * 시작할 수 있는 채움을 점유해 커밋한 뒤, 덮는 범위의 평가를 평가 ID 순으로 {@link BackfillSchedule#BATCH} 건씩 묶음마다 한 트랜잭션에서
 * 잠가 읽는다. 평가마다 평가기가 원본 응답을 다시 읽고, 체계와 맞으면 그 버전의 채울 저장 기준으로 거른 태그 가운데 없는 행만 더한다. 묶음 끝에
 * 커서 · 버전별 건수 · 점유를 토큰이 맞을 때만 쓴다 — 점유를 잃었으면 그 묶음을 되돌리고 멈춘다. 다 돌면 끝낸다.
 * <p>
 * 다시 읽기의 예상 못한 예외는 그 평가를 읽을 수 없음으로 세고 이어 간다 — 저장된 행만 보는 결정적 계산이라 멈추면 같은 커서에서 다시 멈춘다.
 * 그 밖의 예외는 그 묶음을 되돌리고 점유를 풀고 멈춘다 — 다음 회차가 커서부터 잇는다.
 */
@Service
public class TagBackfillRound {

	private static final Logger logger = LoggerFactory.getLogger(TagBackfillRound.class);

	private final TagBackfillRepository backfills;

	private final EvaluationResultRepository results;

	private final ThresholdRepository thresholds;

	private final TagSetViews views;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	TagBackfillRound(TagBackfillRepository backfills, EvaluationResultRepository results,
			ThresholdRepository thresholds, TagSetViews views, TransactionTemplate transaction) {
		this(backfills, results, thresholds, views, transaction, InstantSource.system());
	}

	TagBackfillRound(TagBackfillRepository backfills, EvaluationResultRepository results,
			ThresholdRepository thresholds, TagSetViews views, TransactionTemplate transaction, InstantSource clock) {
		this.backfills = backfills;
		this.results = results;
		this.thresholds = thresholds;
		this.views = views;
		this.transaction = transaction;
		this.clock = clock;
	}

	/** 회차 하나를 돈다 — 평가기는 작업 진입점이 넘긴다 */
	public void run(ArticleEvaluator evaluator) {
		Objects.requireNonNull(evaluator, "evaluator");
		UUID token = UUID.randomUUID();
		Optional<TagBackfill> claimed = this.transaction.execute(status -> this.backfills
			.lockDue(this.clock.instant())
			.map(due -> {
				TagBackfill claim = due.claim(token, this.clock.instant());
				this.backfills.saveLocked(claim);
				return claim;
			}));
		if (claimed == null || claimed.isEmpty()) {
			return;
		}
		TagBackfill backfill = claimed.get();
		Map<TagSetId, Target> targets = new HashMap<>();
		this.backfills.versions(backfill.id())
			.forEach((version, plan) -> targets.put(version, new Target(this.views.of(version), fill(plan))));
		long cursor = backfill.cursor();
		while (true) {
			Batch batch;
			try {
				batch = batch(evaluator, backfill, token, cursor, targets);
			}
			catch (RuntimeException ex) {
				this.transaction.executeWithoutResult(status -> this.backfills.release(backfill.id(), token));
				logger.error("빠진 태그 채움 {} 을 멈췄다 — 다음 회차가 이어서 한다", backfill.id(), ex);
				return;
			}
			switch (batch) {
				case Batch.Advanced advanced -> cursor = advanced.cursor();
				case Batch.Finished finished -> {
					this.backfills.run(backfill.id()).ifPresent(TagBackfillRound::logFinished);
					return;
				}
				case Batch.LeaseLost lost -> {
					logger.info("빠진 태그 채움 {} 의 점유를 다른 회차가 가져가 멈췄다", backfill.id());
					return;
				}
			}
		}
	}

	/** 묶음 하나 — 한 트랜잭션에서 잠가 읽고 더하고 진행을 쓴다 */
	private Batch batch(ArticleEvaluator evaluator, TagBackfill backfill, UUID token, long cursor,
			Map<TagSetId, Target> targets) {
		return Objects.requireNonNull(this.transaction.execute(status -> {
			List<StoredResponse> responses = this.results.lockResponses(backfill.from(), backfill.to(), cursor,
					BackfillSchedule.BATCH);
			if (responses.isEmpty()) {
				return this.backfills.finish(backfill.id(), token, this.clock.instant()) ? new Batch.Finished()
						: new Batch.LeaseLost();
			}
			Map<TagSetId, BackfillCounts> counts = new LinkedHashMap<>();
			for (StoredResponse response : responses) {
				Target target = targets.get(response.tagSetId());
				if (target == null) {
					throw new IllegalStateException("채움 계획에 없는 버전의 평가다 — 평가 " + response.evaluationId());
				}
				counts.merge(response.tagSetId(), fill(evaluator, backfill, response, target), BackfillCounts::plus);
			}
			long last = responses.getLast().evaluationId();
			if (!this.backfills.advance(backfill.id(), token, last, counts,
					BackfillSchedule.leaseUntil(this.clock.instant()))) {
				status.setRollbackOnly();
				return new Batch.LeaseLost();
			}
			return new Batch.Advanced(last);
		}));
	}

	/** 평가 하나를 다시 읽어 빠진 태그를 더한다 — 그 평가의 건수 */
	private BackfillCounts fill(ArticleEvaluator evaluator, TagBackfill backfill, StoredResponse response,
			Target target) {
		RereadResult result;
		try {
			result = evaluator.reread(response.evaluator(), response.rawResponse(), target.tagSet());
		}
		catch (RuntimeException ex) {
			logger.error("빠진 태그 채움 {} 에서 평가기가 원본 응답을 다시 읽다 예상 못한 예외를 던졌다 — 평가 {}", backfill.id(),
					response.evaluationId(), ex);
			return new BackfillCounts(1, 0, 0, 1);
		}
		return switch (result) {
			case Evaluation evaluation -> {
				if (ResultCheck.mismatch(evaluation, target.tagSet()).isPresent()) {
					yield new BackfillCounts(1, 0, 0, 1);
				}
				int added = this.results.addMissingTags(response.evaluationId(), response.articleId(),
						EvaluationRecord.storedTags(evaluation, target.tagSet(), target.thresholds()));
				yield new BackfillCounts(1, added, 0, 0);
			}
			case RereadFailure failure -> switch (failure.kind()) {
				case OTHER_EVALUATOR -> new BackfillCounts(1, 0, 1, 0);
				case UNREADABLE -> new BackfillCounts(1, 0, 0, 1);
			};
		};
	}

	/** 그 버전의 채울 기준 — 저장 기준은 요청 때 고정한 값, 태그 기준은 거르기에 쓰이지 않아 지금 값 그대로 */
	private TagSetThresholds fill(BackfillVersionPlan plan) {
		TagSetThresholds current = this.thresholds.current(plan.version());
		return new TagSetThresholds(plan.version(),
				new TagThresholds(MultiTagKind.TOPIC, current.topic().tagThreshold(), plan.topicStoreThreshold()),
				new TagThresholds(MultiTagKind.REGION, current.region().tagThreshold(), plan.regionStoreThreshold()));
	}

	private static void logFinished(TagBackfillRunView run) {
		logger.info("빠진 태그 채움 {} 을 끝냈다 — 평가 {}건, 더한 태그 {}건, 건너뜀 {}건", run.id(), run.processed(), run.tagsAdded(),
				run.skippedOtherEvaluator() + run.skippedUnreadable());
	}

	/** 버전 하나의 체계와 채울 기준 */
	private record Target(TagSetView tagSet, TagSetThresholds thresholds) {
	}

	/** 묶음 하나의 결과 */
	private sealed interface Batch {

		/** 진행을 썼다 — 다음 묶음은 이 평가 ID 뒤부터 */
		record Advanced(long cursor) implements Batch {
		}

		/** 덮는 범위를 다 돌아 끝냈다 */
		record Finished() implements Batch {
		}

		/** 점유를 다른 회차가 가져갔다 — 이 묶음은 되돌렸다 */
		record LeaseLost() implements Batch {
		}

	}

}
