package net.yozmnews.evaluation.application;

import java.time.InstantSource;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.yozmnews.evaluation.ArticleTagging;
import net.yozmnews.evaluation.TagQuestions;
import net.yozmnews.evaluation.TagSetDirectory;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.TaggingJudgement;
import net.yozmnews.evaluation.TaggingQuery;
import net.yozmnews.evaluation.domain.queue.EvaluationLease;
import net.yozmnews.evaluation.domain.queue.EvaluationQueueRepository;
import net.yozmnews.evaluation.domain.queue.PendingEvaluation;
import net.yozmnews.evaluation.domain.result.EvaluationRecord;
import net.yozmnews.evaluation.domain.result.EvaluationResultRepository;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 평가 회차 하나 — docs/specs/tag.md §4 · tag-R19 · tag-R20.
 * <p>
 * 대기를 점유해 커밋한 뒤, 기사마다 차례로 {@code evaluation} 의 태그 판정을 트랜잭션 밖에서 부른다 — 기사 내용 · 피드 맥락은 관문이
 * 기사 ID 로 읽는다. 결과는 기사마다 새 트랜잭션에서 점유 토큰이 맞을 때만 반영한다. 평가기 사용 불가 · 태그 판정의 예외(결과가 질문
 * 묶음과 어긋남 · 평가기의 예상 못한 예외)면 그 기사부터 남은 점유를 풀고 회차를 멈춘다.
 * <p>
 * 결과를 반영하는 트랜잭션은 먼저 표를 잠가 회차가 읽은 버전이 아직 활성인지 본다. 은퇴했으면 그 기사의 점유만 풀고(판정됐으면 그 버전의
 * 평가는 저장한다) 남은 점유를 풀어 회차를 멈춘다 — 대기 행은 기사에 지금 활성 버전의 평가가 있게 된 트랜잭션에서만 지운다(tag-R19).
 */
@Service
public class EvaluationRound {

	private static final Logger logger = LoggerFactory.getLogger(EvaluationRound.class);

	private final PendingEvaluations pending;

	private final TagSetDirectory directory;

	private final ThresholdRepository thresholds;

	private final ArticleTagging tagging;

	private final EvaluationResultRepository results;

	private final EvaluationQueueRepository queue;

	private final TagSetRepository tagSets;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	EvaluationRound(PendingEvaluations pending, TagSetDirectory directory, ThresholdRepository thresholds,
			ArticleTagging tagging, EvaluationResultRepository results, EvaluationQueueRepository queue,
			TagSetRepository tagSets, TransactionTemplate transaction) {
		this(pending, directory, thresholds, tagging, results, queue, tagSets, transaction, InstantSource.system());
	}

	EvaluationRound(PendingEvaluations pending, TagSetDirectory directory, ThresholdRepository thresholds,
			ArticleTagging tagging, EvaluationResultRepository results, EvaluationQueueRepository queue,
			TagSetRepository tagSets, TransactionTemplate transaction, InstantSource clock) {
		this.pending = pending;
		this.directory = directory;
		this.thresholds = thresholds;
		this.tagging = tagging;
		this.results = results;
		this.queue = queue;
		this.tagSets = tagSets;
		this.transaction = transaction;
		this.clock = clock;
	}

	/** 회차 하나를 돈다 */
	public void run() {
		List<PendingEvaluation> claimed = this.pending.claimDue(this.clock.instant());
		if (claimed.isEmpty()) {
			return;
		}
		TagSetView tagSet = this.directory.activeTagSet();
		TagSetThresholds thresholds = this.thresholds.current(tagSet.id());
		TagQuestions questions = tagSet.questions();
		for (int i = 0; i < claimed.size(); i++) {
			PendingEvaluation claim = claimed.get(i);
			if (this.results.exists(claim.articleId(), tagSet.id())) {
				if (!writeIfCurrent(claimed, i, tagSet.id(),
						() -> this.queue.deleteIfLeased(claim.articleId(), token(claim)))) {
					return;
				}
				continue;
			}
			TaggingJudgement judgement;
			try {
				judgement = this.tagging.judge(new TaggingQuery(claim.articleId(), questions));
			}
			catch (RuntimeException ex) {
				release(claimed.subList(i, claimed.size()));
				logger.error("태그 판정이 실패해 회차를 멈췄다 — 기사 {}", claim.articleId().value(), ex);
				return;
			}
			switch (judgement) {
				case TaggingJudgement.ArticleMissing missing -> {
					delete(claim);
					logger.info("평가 대기 기사가 지워져 대기에서 뺐다 — 기사 {}", claim.articleId().value());
				}
				case TaggingJudgement.Judged judged -> {
					EvaluationRecord record = EvaluationRecord.of(claim.articleId(), judged.judgement(), judged.answers(),
							tagSet, thresholds, this.clock.instant());
					if (!writeIfCurrent(claimed, i, tagSet.id(), () -> {
						if (this.queue.deleteIfLeased(claim.articleId(), token(claim))) {
							this.results.insert(record);
						}
					}, () -> {
						// 은퇴한 버전의 평가도 유효하다 — 저장하되 대기 행은 남겨 새 활성 버전으로 다시 평가한다
						if (!this.results.exists(claim.articleId(), tagSet.id())) {
							this.results.insert(record);
						}
					})) {
						return;
					}
				}
				case TaggingJudgement.Failed failure -> {
					@Nullable PendingEvaluation failed = switch (failure.kind()) {
						case TRANSIENT -> claim.afterTransient(failure.message(), this.clock.instant());
						case ARTICLE_REJECTED -> claim.afterRejected(failure.message());
						case EVALUATOR_UNAVAILABLE -> {
							release(claimed.subList(i, claimed.size()));
							logger.warn("평가기를 쓸 수 없어 회차를 멈췄다 — 기사 {}: {}", claim.articleId().value(),
									failure.message());
							yield null;
						}
					};
					if (failed == null) {
						return;
					}
					if (!writeIfCurrent(claimed, i, tagSet.id(),
							() -> this.queue.replaceIfLeased(token(claim), failed))) {
						return;
					}
				}
			}
		}
	}

	/** {@link #writeIfCurrent(List, int, TagSetId, Runnable, Runnable)} — 은퇴했을 때 점유를 푸는 것 말고는 쓰지 않는다 */
	private boolean writeIfCurrent(List<PendingEvaluation> claimed, int index, TagSetId version, Runnable whenActive) {
		return writeIfCurrent(claimed, index, version, whenActive, () -> {
		});
	}

	/**
	 * 회차가 읽은 버전이 아직 활성일 때만 쓴다 — 새 트랜잭션에서 표를 활성 전환과 충돌하는 모드로 잠근 뒤 본다. 은퇴했으면 이 기사의 점유를
	 * 풀고(풀었을 때만 {@code whenReleased}), 트랜잭션 뒤에 남은 점유를 풀고 회차를 멈춘다.
	 * @param claimed 회차가 점유한 기사 — 그 가운데 {@code index} 가 이 기사다
	 * @param whenActive 활성일 때의 쓰기
	 * @param whenReleased 은퇴했고 이 기사의 점유를 풀었을 때의 쓰기
	 * @return 활성이었으면 true — false 면 회차를 멈췄다
	 */
	private boolean writeIfCurrent(List<PendingEvaluation> claimed, int index, TagSetId version, Runnable whenActive,
			Runnable whenReleased) {
		PendingEvaluation claim = claimed.get(index);
		boolean active = Boolean.TRUE.equals(this.transaction.execute(status -> {
			this.tagSets.lockAgainstChange();
			if (this.tagSets.isActive(version.value())) {
				whenActive.run();
				return true;
			}
			if (unlease(claim)) {
				whenReleased.run();
			}
			return false;
		}));
		if (!active) {
			stopRetired(claimed, index);
		}
		return active;
	}

	/** 은퇴한 버전으로 평가한 기사 뒤의 점유를 풀고 회차를 멈춘다 */
	private void stopRetired(List<PendingEvaluation> claimed, int index) {
		release(claimed.subList(index + 1, claimed.size()));
		logger.info("활성 태그 체계가 바뀌어 회차를 멈췄다 — 기사 {}", claimed.get(index).articleId().value());
	}

	private void delete(PendingEvaluation claim) {
		this.transaction.executeWithoutResult(status -> this.queue.deleteIfLeased(claim.articleId(), token(claim)));
	}

	/** 남은 점유를 푼다 — 기사마다 트랜잭션 하나 */
	private void release(List<PendingEvaluation> remaining) {
		for (PendingEvaluation claim : remaining) {
			this.transaction.executeWithoutResult(status -> unlease(claim));
		}
	}

	/**
	 * 이 기사의 점유를 푼다 — 시도 수 · 평가 가능 시각은 그대로. 트랜잭션 안에서 부른다.
	 * @return 풀었으면 true — 점유가 이미 바뀌었으면 false
	 */
	private boolean unlease(PendingEvaluation claim) {
		return this.queue.replaceIfLeased(token(claim), claim.released());
	}

	private static UUID token(PendingEvaluation claim) {
		EvaluationLease lease = Objects.requireNonNull(claim.lease(), "점유한 대기 행이다");
		return lease.token();
	}

}
