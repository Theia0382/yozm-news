package net.yozmnews.evaluation.application;

import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleEntryView;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.TagSetDirectory;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.Evaluation;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;
import net.yozmnews.evaluation.domain.evaluator.EvaluationInput;
import net.yozmnews.evaluation.domain.evaluator.EvaluationResult;
import net.yozmnews.evaluation.domain.evaluator.FeedContext;
import net.yozmnews.evaluation.domain.queue.EvaluationLease;
import net.yozmnews.evaluation.domain.queue.EvaluationQueueRepository;
import net.yozmnews.evaluation.domain.queue.PendingEvaluation;
import net.yozmnews.evaluation.domain.result.EvaluationRecord;
import net.yozmnews.evaluation.domain.result.EvaluationResultRepository;
import net.yozmnews.evaluation.domain.result.ResultCheck;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabel;
import net.yozmnews.source.FeedLabels;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 평가 회차 하나 — docs/specs/evaluation.md §4 · evaluation-R19 · evaluation-R20.
 * <p>
 * 대기를 점유해 커밋한 뒤, 트랜잭션 밖에서 기사 · 피드 맥락을 읽고 기사마다 차례로 평가기를 부른다. 결과는 기사마다 새 트랜잭션에서 점유 토큰이
 * 맞을 때만 반영한다. 평가기 사용 불가 · 결과가 체계와 어긋남 · 평가기의 예상 못한 예외면 그 기사부터 남은 점유를 풀고 회차를 멈춘다.
 */
@Service
public class EvaluationRound {

	private static final Logger logger = LoggerFactory.getLogger(EvaluationRound.class);

	private final PendingEvaluations pending;

	private final TagSetDirectory directory;

	private final ThresholdRepository thresholds;

	private final ArticleDirectory articles;

	private final FeedLabels feedLabels;

	private final EvaluationResultRepository results;

	private final EvaluationQueueRepository queue;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	EvaluationRound(PendingEvaluations pending, TagSetDirectory directory, ThresholdRepository thresholds,
			ArticleDirectory articles, FeedLabels feedLabels, EvaluationResultRepository results,
			EvaluationQueueRepository queue, TransactionTemplate transaction) {
		this(pending, directory, thresholds, articles, feedLabels, results, queue, transaction,
				InstantSource.system());
	}

	EvaluationRound(PendingEvaluations pending, TagSetDirectory directory, ThresholdRepository thresholds,
			ArticleDirectory articles, FeedLabels feedLabels, EvaluationResultRepository results,
			EvaluationQueueRepository queue, TransactionTemplate transaction, InstantSource clock) {
		this.pending = pending;
		this.directory = directory;
		this.thresholds = thresholds;
		this.articles = articles;
		this.feedLabels = feedLabels;
		this.results = results;
		this.queue = queue;
		this.transaction = transaction;
		this.clock = clock;
	}

	/** 회차 하나를 돈다 — 평가기는 작업 진입점이 넘긴다 */
	public void run(ArticleEvaluator evaluator) {
		Objects.requireNonNull(evaluator, "evaluator");
		List<PendingEvaluation> claimed = this.pending.claimDue(this.clock.instant());
		if (claimed.isEmpty()) {
			return;
		}
		TagSetView tagSet = this.directory.activeTagSet();
		TagSetThresholds thresholds = this.thresholds.current(tagSet.id());
		Map<ArticleId, ArticleContent> contents = this.articles
			.findAll(claimed.stream().map(PendingEvaluation::articleId).toList());
		Set<FeedId> feeds = contents.values()
			.stream()
			.flatMap(content -> content.entries().stream())
			.map(ArticleEntryView::feedId)
			.collect(Collectors.toSet());
		Map<FeedId, FeedLabel> labels = this.feedLabels.labels(feeds);
		for (int i = 0; i < claimed.size(); i++) {
			PendingEvaluation claim = claimed.get(i);
			ArticleContent content = contents.get(claim.articleId());
			if (content == null) {
				delete(claim);
				logger.info("평가 대기 기사가 지워져 대기에서 뺐다 — 기사 {}", claim.articleId().value());
				continue;
			}
			if (this.results.exists(claim.articleId(), tagSet.id())) {
				delete(claim);
				continue;
			}
			EvaluationInput input = input(content, labels);
			EvaluationResult result;
			try {
				result = evaluator.evaluate(input, tagSet);
			}
			catch (RuntimeException ex) {
				release(claimed.subList(i, claimed.size()));
				logger.error("평가기가 예상 못한 예외를 던져 회차를 멈췄다 — 기사 {}", claim.articleId().value(), ex);
				return;
			}
			switch (result) {
				case Evaluation evaluation -> {
					Optional<String> mismatch = ResultCheck.mismatch(evaluation, tagSet);
					if (mismatch.isPresent()) {
						release(claimed.subList(i, claimed.size()));
						logger.error("평가 결과가 체계와 맞지 않아 회차를 멈췄다 — 기사 {}: {}", claim.articleId().value(),
								mismatch.get());
						return;
					}
					EvaluationRecord record = EvaluationRecord.of(claim.articleId(), evaluation, tagSet, thresholds,
							this.clock.instant());
					this.transaction.executeWithoutResult(status -> {
						if (this.queue.deleteIfLeased(claim.articleId(), token(claim))) {
							this.results.insert(record);
						}
					});
				}
				case EvaluationFailure failure -> {
					switch (failure.kind()) {
						case TRANSIENT -> replace(claim, claim.afterTransient(failure.message(), this.clock.instant()));
						case ARTICLE_REJECTED -> replace(claim, claim.afterRejected(failure.message()));
						case EVALUATOR_UNAVAILABLE -> {
							release(claimed.subList(i, claimed.size()));
							logger.warn("평가기를 쓸 수 없어 회차를 멈췄다 — 기사 {}: {}", claim.articleId().value(),
									failure.message());
							return;
						}
					}
				}
			}
		}
	}

	/** 평가 입력 — 피드 맥락은 엔트리 순서 그대로, 이름 · 섹션이 없는 피드(그새 지움)는 비운다 */
	private static EvaluationInput input(ArticleContent content, Map<FeedId, FeedLabel> labels) {
		List<FeedContext> feeds = content.entries().stream().map(entry -> {
			@Nullable FeedLabel label = labels.get(entry.feedId());
			return FeedContext.limited((label != null) ? label.sourceName() : null,
					(label != null) ? label.section() : null, entry.categories());
		}).toList();
		String summary = content.summary();
		return new EvaluationInput(content.title(), (summary == null || summary.isBlank()) ? null : summary, feeds);
	}

	private void delete(PendingEvaluation claim) {
		this.transaction.executeWithoutResult(status -> this.queue.deleteIfLeased(claim.articleId(), token(claim)));
	}

	private void replace(PendingEvaluation claim, PendingEvaluation next) {
		this.transaction.executeWithoutResult(status -> this.queue.replaceIfLeased(token(claim), next));
	}

	/** 남은 점유를 푼다 — 시도 수 · 평가 가능 시각은 그대로 */
	private void release(List<PendingEvaluation> remaining) {
		for (PendingEvaluation claim : remaining) {
			replace(claim, claim.released());
		}
	}

	private static UUID token(PendingEvaluation claim) {
		EvaluationLease lease = Objects.requireNonNull(claim.lease(), "점유한 대기 행이다");
		return lease.token();
	}

}
