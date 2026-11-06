package net.yozmnews.event.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EventDescriptor;
import net.yozmnews.evaluation.EventMembership;
import net.yozmnews.evaluation.MembershipJudgement;
import net.yozmnews.evaluation.MembershipQuery;
import net.yozmnews.event.domain.assignment.AssignmentCriteria;
import net.yozmnews.event.domain.assignment.AssignmentQueueRepository;
import net.yozmnews.event.domain.assignment.AssignmentRule;
import net.yozmnews.event.domain.assignment.AssignmentSchedule;
import net.yozmnews.event.domain.assignment.CandidateScoring;
import net.yozmnews.event.domain.assignment.CriteriaRepository;
import net.yozmnews.event.domain.assignment.DecidedBy;
import net.yozmnews.event.domain.assignment.Decision;
import net.yozmnews.event.domain.assignment.DecisionPhase;
import net.yozmnews.event.domain.assignment.PendingAssignment;
import net.yozmnews.event.domain.assignment.StoredCandidate;
import net.yozmnews.event.domain.event.EventArticle;
import net.yozmnews.event.domain.event.EventArticleRepository;
import net.yozmnews.event.domain.event.EventRepository;
import net.yozmnews.event.domain.event.EventStatus;
import net.yozmnews.event.domain.event.LockedEvent;
import net.yozmnews.event.domain.feature.ArticleEmbedder;
import net.yozmnews.event.domain.feature.MatchFeatures;
import net.yozmnews.event.domain.feature.MatchFeaturesRepository;
import net.yozmnews.event.domain.intake.IntakeQueueRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 배정 회차 하나 — docs/specs/event.md §4.
 * <p>
 * 배정 대기를 점유해 커밋한 뒤, 기사마다 점유 순으로 하나씩 판정하고 짧은 트랜잭션에서 쓴다 — 앞 기사가 연 사건을 뒤 기사가 후보로 본다.
 * 사건 소속 판정은 트랜잭션 밖에서 부른다. 판정 · 적용 규칙은 {@code domain/assignment} 가 정하고, 이 클래스는 순서 · 트랜잭션 경계 ·
 * 실패 갈래의 실행만 맡는다. 예외를 밖으로 던지지 않는다. 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
public class AssignmentRound {

	private static final Logger logger = LoggerFactory.getLogger(AssignmentRound.class);

	private final AssignmentQueueRepository queue;

	private final IntakeQueueRepository intake;

	private final MatchFeaturesRepository features;

	private final EventRepository events;

	private final EventArticleRepository assignments;

	private final CriteriaRepository criteriaHistory;

	private final AssignmentCriteria criteria;

	private final ArticleDirectory articles;

	private final EventMembership membership;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	AssignmentRound(AssignmentQueueRepository queue, IntakeQueueRepository intake, MatchFeaturesRepository features,
			EventRepository events, EventArticleRepository assignments, CriteriaRepository criteriaHistory,
			AssignmentCriteria criteria, ArticleDirectory articles, EventMembership membership,
			TransactionTemplate transaction) {
		this(queue, intake, features, events, assignments, criteriaHistory, criteria, articles, membership, transaction,
				InstantSource.system());
	}

	/** 시험이 시계를 넣는다 */
	AssignmentRound(AssignmentQueueRepository queue, IntakeQueueRepository intake, MatchFeaturesRepository features,
			EventRepository events, EventArticleRepository assignments, CriteriaRepository criteriaHistory,
			AssignmentCriteria criteria, ArticleDirectory articles, EventMembership membership,
			TransactionTemplate transaction, InstantSource clock) {
		this.queue = queue;
		this.intake = intake;
		this.features = features;
		this.events = events;
		this.assignments = assignments;
		this.criteriaHistory = criteriaHistory;
		this.criteria = criteria;
		this.articles = articles;
		this.membership = membership;
		this.transaction = transaction;
		this.clock = clock;
	}

	/** 회차 하나를 돈다 — 임베더는 작업 진입점이 넘기고, 지금 모델 버전을 정한다 */
	public void run(ArticleEmbedder embedder) {
		Objects.requireNonNull(embedder, "embedder");
		String model = embedder.info().modelVersion();
		Instant now = this.clock.instant();
		List<PendingAssignment> claimed = claim(now);
		if (claimed.isEmpty()) {
			return;
		}
		long criteriaId = Objects.requireNonNull(
				this.transaction.execute(status -> this.criteriaHistory.register(this.criteria, now)), "criteriaId");
		Map<ArticleId, ArticleContent> contents = this.articles
			.findAll(claimed.stream().map(PendingAssignment::articleId).toList());
		for (int i = 0; i < claimed.size(); i++) {
			PendingAssignment claim = claimed.get(i);
			Step step;
			try {
				// 기사마다 시계를 다시 읽는다 — 회차가 길어도 재시도 시각 · 배정 시각이 그 기사를 처리한 때다
				step = assign(claim, contents.get(claim.articleId()), model, criteriaId, this.clock.instant());
			}
			catch (RuntimeException ex) {
				release(claimed.subList(i, claimed.size()));
				logger.error("배정 회차가 예상하지 못한 오류로 멈췄다 — 기사 {}, 남은 기사 {}건", claim.articleId().value(),
						claimed.size() - i, ex);
				return;
			}
			switch (step) {
				case Step.Next next -> {
				}
				case Step.Stop stop -> {
					release(claimed.subList(i, claimed.size()));
					logger.warn("평가기를 쓸 수 없어 배정 회차를 멈췄다 — 남은 기사 {}건: {}", claimed.size() - i, stop.message());
					return;
				}
			}
		}
	}

	private List<PendingAssignment> claim(Instant now) {
		List<PendingAssignment> claimed = this.transaction.execute(status -> {
			List<PendingAssignment> leased = new ArrayList<>();
			for (PendingAssignment due : this.queue.lockDue(now, AssignmentSchedule.BATCH_SIZE)) {
				PendingAssignment next = due.claim(UUID.randomUUID(), now);
				this.queue.saveLocked(next);
				leased.add(next);
			}
			return leased;
		});
		return Objects.requireNonNull(claimed, "claimed");
	}

	private Step assign(PendingAssignment claim, @Nullable ArticleContent content, String model, long criteriaId,
			Instant now) {
		ArticleId article = claim.articleId();
		UUID token = token(claim);
		if (content == null) {
			this.transaction.executeWithoutResult(status -> this.queue.deleteIfLeased(article, token));
			logger.info("배정 대기 기사가 지워져 대기에서 뺐다 — 기사 {}", article.value());
			return new Step.Next();
		}
		if (this.assignments.exists(article)) {
			this.transaction.executeWithoutResult(status -> this.queue.deleteIfLeased(article, token));
			return new Step.Next();
		}
		Optional<MatchFeatures> found = this.features.find(article, model);
		if (found.isEmpty()) {
			// 모델이 바뀌어 지금 모델의 표현이 없다 — 표현을 다시 만들게 사건 묶기 대기로 돌려보낸다
			this.transaction.executeWithoutResult(status -> {
				if (this.queue.deleteIfLeased(article, token)) {
					this.intake.enqueue(article, now);
				}
			});
			return new Step.Next();
		}
		MatchFeatures representation = found.get();
		List<StoredCandidate> stored = scored(representation, content.publishedAt(), model);
		Resolution resolution = switch (AssignmentRule.decide(stored, this.criteria, DecisionPhase.FIRST)) {
			case Decision.Ask ask -> judge(claim, ask, stored, model, now);
			case Decision.Attach attach -> new Resolution.Decided(attach, stored);
			case Decision.Open open -> new Resolution.Decided(open, stored);
			case Decision.Keep keep -> throw new IllegalStateException("처음 판정은 유지를 내지 않는다");
		};
		return switch (resolution) {
			case Resolution.Decided decided -> {
				write(claim, decided.decision(), new EventArticleDraft(article, content.publishedAt(), representation,
						decided.stored(), criteriaId), model, now);
				yield new Step.Next();
			}
			case Resolution.Finished finished -> finished.step();
		};
	}

	/**
	 * 띠의 후보를 사건 소속 판정에 묻는다 — 트랜잭션 밖. 서술 기사가 있는 후보가 없으면 묻지 않고 판정 뒤 규칙으로 정한다.
	 */
	private Resolution judge(PendingAssignment claim, Decision.Ask ask, List<StoredCandidate> stored, String model,
			Instant now) {
		ArticleId article = claim.articleId();
		UUID token = token(claim);
		List<EventDescriptor> descriptors = describe(ask.eventIds(), model);
		if (descriptors.isEmpty()) {
			return new Resolution.Decided(AssignmentRule.decide(stored, this.criteria, DecisionPhase.AFTER_JUDGEMENT),
					stored);
		}
		return switch (this.membership.judge(new MembershipQuery(article, descriptors))) {
			case MembershipJudgement.Judged judged -> {
				List<StoredCandidate> judgedStored = stored.stream()
					.map(candidate -> new StoredCandidate(candidate.eventId(), candidate.score(),
							judged.probabilities().getOrDefault(candidate.eventId(), candidate.probability())))
					.toList();
				yield new Resolution.Decided(
						AssignmentRule.decide(judgedStored, this.criteria, DecisionPhase.AFTER_JUDGEMENT), judgedStored);
			}
			case MembershipJudgement.Failed failed -> switch (failed.kind()) {
				case TRANSIENT -> {
					PendingAssignment next = claim.afterTransient(now);
					if (next.exhausted()) {
						logger.warn("사건 소속 판정의 일시 실패가 상한에 이르러 새 사건으로 끝낸다 — 기사 {}: {}", article.value(),
								failed.message());
						yield failedOpen(stored);
					}
					this.transaction.executeWithoutResult(
							status -> this.queue.retryIfLeased(article, token, next.attempts(), next.availableAt()));
					logger.info("사건 소속 판정이 일시 실패해 다시 시도한다 — 기사 {}, 시도 {}: {}", article.value(),
							next.attempts(), failed.message());
					yield new Resolution.Finished(new Step.Next());
				}
				case ARTICLE_REJECTED -> {
					logger.warn("평가기가 기사를 거부해 새 사건으로 끝낸다 — 기사 {}: {}", article.value(), failed.message());
					yield failedOpen(stored);
				}
				case EVALUATOR_UNAVAILABLE -> new Resolution.Finished(new Step.Stop(failed.message()));
			};
			case MembershipJudgement.ArticleMissing missing -> {
				this.transaction.executeWithoutResult(status -> this.queue.deleteIfLeased(article, token));
				logger.info("배정 대기 기사가 판정 중에 지워져 대기에서 뺐다 — 기사 {}", article.value());
				yield new Resolution.Finished(new Step.Next());
			}
		};
	}

	/** 판정 실패로 끝낸 기사 — 실패 표지를 단 새 사건. 후보는 확률 없이 남긴다 */
	private static Resolution failedOpen(List<StoredCandidate> stored) {
		return new Resolution.Decided(new Decision.Open(bestScore(stored), DecidedBy.JUDGEMENT_FAILED), stored);
	}

	/** 후보 점수를 매기고 저장 기준 이상만 남긴다 — 점수가 NaN 이면(영 벡터 중심) 비교가 거짓이라 빠진다 */
	private List<StoredCandidate> scored(MatchFeatures representation, Instant publishedAt, String model) {
		return this.events
			.candidates(model, representation.embedding().values(), publishedAt.minus(this.criteria.window()),
					publishedAt.plus(this.criteria.window()))
			.stream()
			.flatMap(event -> CandidateScoring
				.score(event, representation.entities(), publishedAt, this.criteria.window())
				.stream())
			.filter(candidate -> candidate.score() >= this.criteria.store())
			.map(candidate -> new StoredCandidate(candidate.eventId(), candidate.score(), null))
			.toList();
	}

	/** 물을 후보마다 서술 기사를 고른다 — 서술 기사가 없는 후보는 뺀다 */
	private List<EventDescriptor> describe(List<Long> eventIds, String model) {
		List<EventDescriptor> descriptors = new ArrayList<>();
		for (long eventId : eventIds) {
			List<ArticleId> describers = this.events.describers(eventId, model, AssignmentSchedule.DESCRIBED_ARTICLES);
			if (!describers.isEmpty()) {
				descriptors.add(new EventDescriptor(eventId, describers));
			}
		}
		return descriptors;
	}

	private static @Nullable Double bestScore(List<StoredCandidate> stored) {
		return stored.stream().map(StoredCandidate::score).max(Comparator.naturalOrder()).orElse(null);
	}

	/**
	 * 판정을 쓴다 — 점유 토큰이 맞아 대기 행을 지웠을 때만. 붙일 사건이 사라졌거나 열려 있지 않거나 모델 버전이 다르면, 또는 다른 경로가 먼저
	 * 배정했으면 롤백하고 새 트랜잭션에서 점유를 풀거나(다음 회차가 다시 판정) 대기 행을 지운다.
	 */
	private void write(PendingAssignment claim, Decision decision, EventArticleDraft draft, String model, Instant now) {
		UUID token = token(claim);
		Written written = Objects.requireNonNull(this.transaction.execute(status -> {
			if (!this.queue.deleteIfLeased(draft.article(), token)) {
				return Written.NOT_LEASED;
			}
			float[] vector = draft.representation().embedding().values();
			List<String> terms = draft.representation().entities();
			@Nullable EventArticle row = switch (decision) {
				case Decision.Attach attach -> {
					Optional<LockedEvent> locked = this.events.lock(attach.eventId());
					if (locked.isEmpty() || locked.get().status() != EventStatus.OPEN
							|| !locked.get().modelVersion().equals(model)) {
						yield null;
					}
					LockedEvent event = locked.get();
					this.events.update(event.joined(vector, draft.publishedAt()), terms, List.of());
					yield new EventArticle(draft.article(), event.id(), draft.publishedAt(), attach.score(), attach.by(),
							draft.criteriaId(), now);
				}
				case Decision.Open open -> {
					long eventId = this.events.open(model, vector, draft.publishedAt(), terms, now);
					yield new EventArticle(draft.article(), eventId, draft.publishedAt(), open.bestScore(), open.by(),
							draft.criteriaId(), now);
				}
				case Decision.Ask ask -> throw new IllegalStateException("물을 후보가 남은 채 쓰려 했다: " + ask);
				case Decision.Keep keep -> throw new IllegalStateException("처음 판정은 유지를 내지 않는다");
			};
			if (row == null) {
				status.setRollbackOnly();
				return Written.EVENT_UNAVAILABLE;
			}
			if (!this.assignments.insertIfAbsent(row, draft.stored())) {
				status.setRollbackOnly();
				return Written.ALREADY_ASSIGNED;
			}
			return Written.WRITTEN;
		}), "written");
		// 롤백하면 대기 행이 점유가 남은 채 되살아난다 — 만료까지 묶이지 않게 새 트랜잭션에서 정리한다
		Runnable afterRollback = switch (written) {
			case EVENT_UNAVAILABLE -> () -> {
				this.transaction.executeWithoutResult(status -> this.queue.releaseIfLeased(draft.article(), token));
				logger.info("붙일 사건이 사라졌거나 닫혀 다음 회차에 다시 판정한다 — 기사 {}", draft.article().value());
			};
			case ALREADY_ASSIGNED ->
				() -> this.transaction.executeWithoutResult(status -> this.queue.deleteIfLeased(draft.article(), token));
			case WRITTEN, NOT_LEASED -> () -> {
			};
		};
		afterRollback.run();
	}

	private void release(List<PendingAssignment> claims) {
		for (PendingAssignment claim : claims) {
			this.transaction.executeWithoutResult(status -> this.queue.releaseIfLeased(claim.articleId(), token(claim)));
		}
	}

	private static UUID token(PendingAssignment claim) {
		return Objects.requireNonNull(claim.lease(), "lease").token();
	}

	/** 기사 하나를 처리한 뒤 회차가 할 일 */
	private sealed interface Step {

		/** 다음 기사로 */
		record Next() implements Step {
		}

		/** 이 기사부터 남은 점유를 풀고 회차를 멈춘다 */
		record Stop(String message) implements Step {
		}

	}

	/** 판정을 마친 결과 — 쓸 판정이거나, 쓰지 않고 끝낸 처리 */
	private sealed interface Resolution {

		/** 쓸 판정과 함께 남길 후보 */
		record Decided(Decision decision, List<StoredCandidate> stored) implements Resolution {
		}

		/** 쓰지 않고 끝냈다 — 재시도로 미뤘거나 기사가 없거나 회차를 멈춘다 */
		record Finished(Step step) implements Resolution {
		}

	}

	/** 쓰기의 결과 */
	private enum Written {

		WRITTEN, NOT_LEASED, EVENT_UNAVAILABLE, ALREADY_ASSIGNED

	}

	/** 쓸 배정의 재료 */
	private record EventArticleDraft(ArticleId article, Instant publishedAt, MatchFeatures representation,
			List<StoredCandidate> stored, long criteriaId) {
	}

}
