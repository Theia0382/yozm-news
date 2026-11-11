package net.yozmnews.event.application;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EventDescriptor;
import net.yozmnews.evaluation.EventMembership;
import net.yozmnews.evaluation.MembershipJudgement;
import net.yozmnews.evaluation.MembershipQuery;
import net.yozmnews.event.AssignmentCriteria;
import net.yozmnews.event.EventStatus;
import net.yozmnews.event.domain.assignment.AssignmentRule;
import net.yozmnews.event.domain.assignment.AssignmentSchedule;
import net.yozmnews.event.domain.assignment.CandidateScoring;
import net.yozmnews.event.domain.assignment.CriteriaRepository;
import net.yozmnews.event.domain.assignment.Decision;
import net.yozmnews.event.domain.assignment.DecisionPhase;
import net.yozmnews.event.domain.assignment.EventSnapshot;
import net.yozmnews.event.domain.assignment.MergeJudgementRepository;
import net.yozmnews.event.domain.assignment.MergeRule;
import net.yozmnews.event.domain.assignment.ScoredCandidate;
import net.yozmnews.event.domain.assignment.StoredCandidate;
import net.yozmnews.event.domain.event.EventArticle;
import net.yozmnews.event.domain.event.EventArticleRepository;
import net.yozmnews.event.domain.event.EventRepository;
import net.yozmnews.event.domain.event.LockedEvent;
import net.yozmnews.event.domain.event.MergeSubject;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 병합 회차 하나 — 지난 확인 뒤 바뀐 열린 사건마다 가까운 열린 사건을 찾아 합친다 · docs/specs/event.md §4 · event-R13.
 * <p>
 * 사건 하나를 기사 자리에 둔 배정이다 — 점수는 {@link CandidateScoring}(구간 대 구간), 판정은 {@link AssignmentRule}, 띠는 사건 소속
 * 판정({@link EventMembership})에 묻는다. 판정할 기사는 바뀐 사건의 서술 기사 하나이고, 받은 확률은 쌍마다 한 번 남겨 다시 묻지 않는다.
 * 기록이 없는 띠 쌍은 배정 때 받은 확률({@link EventArticleRepository#priorProbabilities})이 확률 기준 미만이면 묻지 않고 합치지
 * 않는다 — 남기지 않아 확인할 때마다 그때 기준과 견준다. 기준 이상이면 묻는다({@link MergeRule#priorProbability}).
 * 핵심 말 겹침의 기준 쪽과 남는 사건은 {@link MergeRule} 이 정한다. 쓰기는 쌍마다 한 트랜잭션에서 배정 행(기사 ID 순) → 사건(ID 순)을
 * 잠그고, 둘 다 열려 있고 모델 버전이 같고 잠근 배정 수가 사건의 기사 수와 같을 때만 합친다 — 아니면 표시하지 않아 다음 회차가 다시 본다.
 * 사건 소속 판정은 트랜잭션 밖에서 부른다. 회차마다 {@link AssignmentSchedule#MERGE_BATCH} 사건까지 본다. 예외를 밖으로 던지지 않는다.
 * 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
public class MergeRound {

	private static final Logger logger = LoggerFactory.getLogger(MergeRound.class);

	private final EventRepository events;

	private final EventArticleRepository assignments;

	private final MergeJudgementRepository judgements;

	private final CriteriaRepository criteriaHistory;

	private final AssignmentCriteria criteria;

	private final EventMembership membership;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	MergeRound(EventRepository events, EventArticleRepository assignments, MergeJudgementRepository judgements,
			CriteriaRepository criteriaHistory, AssignmentSettings settings, EventMembership membership,
			TransactionTemplate transaction) {
		this(events, assignments, judgements, criteriaHistory, settings.criteria(), membership, transaction,
				InstantSource.system());
	}

	/** 시험이 시계를 넣는다 */
	MergeRound(EventRepository events, EventArticleRepository assignments, MergeJudgementRepository judgements,
			CriteriaRepository criteriaHistory, AssignmentCriteria criteria, EventMembership membership,
			TransactionTemplate transaction, InstantSource clock) {
		this.events = events;
		this.assignments = assignments;
		this.judgements = judgements;
		this.criteriaHistory = criteriaHistory;
		this.criteria = criteria;
		this.membership = membership;
		this.transaction = transaction;
		this.clock = clock;
	}

	/** 회차 하나를 돈다 */
	public void run() {
		Set<Long> touched = new HashSet<>();
		int merged = 0;
		@Nullable Long current = null;
		try {
			long criteriaId = Objects.requireNonNull(
					this.transaction.execute(status -> this.criteriaHistory.register(this.criteria, this.clock.instant())),
					"criteriaId");
			for (MergeSubject subject : this.events.mergeDue(criteriaId, AssignmentSchedule.MERGE_BATCH)) {
				// 이 회차에서 앞서 합친 사건이다 — 읽어 둔 값이 낡았고, 남는 쪽은 revision 이 늘어 다음 회차가 다시 본다
				if (touched.contains(subject.id())) {
					continue;
				}
				current = subject.id();
				Step step = check(subject, criteriaId, this.clock.instant());
				switch (step) {
					case Step.Merged done -> {
						touched.add(done.survivor());
						touched.add(done.absorbed());
						merged++;
					}
					case Step.Next next -> {
					}
					case Step.Stop stop -> {
						logger.warn("평가기를 쓸 수 없어 병합 회차를 멈췄다 — 사건 {}: {}", subject.id(), stop.message());
						return;
					}
				}
			}
		}
		catch (RuntimeException ex) {
			logger.error("병합 회차가 예상하지 못한 오류로 멈췄다 — 사건 {}", current, ex);
			return;
		}
		finally {
			if (merged > 0) {
				logger.info("사건 {}건을 합쳤다", merged);
			}
		}
	}

	/** 바뀐 사건 하나를 본다 — 판정은 트랜잭션 밖, 합치기는 한 트랜잭션 */
	private Step check(MergeSubject subject, long criteriaId, Instant now) {
		List<StoredCandidate> stored = scored(subject);
		Resolution resolution = switch (AssignmentRule.decide(stored, this.criteria, DecisionPhase.FIRST)) {
			case Decision.Ask ask -> judge(subject, ask, stored, now);
			case Decision.Attach attach -> new Resolution.Decided(attach);
			case Decision.Open open -> new Resolution.Decided(open);
			case Decision.Keep keep -> throw new IllegalStateException("병합은 유지를 내지 않는다");
		};
		return switch (resolution) {
			case Resolution.Decided decided -> apply(subject, decided.decision(), criteriaId, now);
			// 일시 실패이거나 서술 기사가 지워졌다 — 표시하지 않아 다음 회차가 다시 본다
			case Resolution.Later later -> new Step.Next();
			case Resolution.Halt halt -> new Step.Stop(halt.message());
		};
	}

	/** 판정을 적용한다 — 붙임이면 합치고, 새 사건이면 합치지 않는다. 합치지 못하면 표시하지 않는다 */
	private Step apply(MergeSubject subject, Decision decision, long criteriaId, Instant now) {
		return switch (decision) {
			case Decision.Attach attach -> {
				Optional<Step.Merged> done = merge(subject.id(), attach.eventId(), now);
				if (done.isEmpty()) {
					logger.info("합칠 사건이 닫혔거나 그새 바뀌어 다음 회차에 다시 본다 — 사건 {} · {}", subject.id(),
							attach.eventId());
					yield new Step.Next();
				}
				// 합쳤으면 revision 이 늘어 표시되지 않는다 — 남는 사건은 다음 회차가 다시 본다(사슬 병합)
				this.events.markMergeChecked(subject.id(), subject.revision(), criteriaId);
				yield done.get();
			}
			case Decision.Open open -> {
				this.events.markMergeChecked(subject.id(), subject.revision(), criteriaId);
				yield new Step.Next();
			}
			case Decision.Ask ask -> throw new IllegalStateException("물을 후보가 남은 채 정하려 했다: " + ask);
			case Decision.Keep keep -> throw new IllegalStateException("병합은 유지를 내지 않는다");
		};
	}

	/**
	 * 후보 사건의 점수를 매겨 저장 기준 이상만 남기고, 쌍마다 남긴 판정 확률을 채운다. 기록이 없는 띠 후보는 배정 때 받은 확률이 확률 기준
	 * 미만일 때만 그 확률로 채운다 — 기준 이상이거나 없으면 확률 없는 후보로 두어 묻는다.
	 */
	private List<StoredCandidate> scored(MergeSubject subject) {
		Duration window = this.criteria.window();
		List<ScoredCandidate> scored = this.events
			.candidates(subject.modelVersion(), subject.centroid(), subject.firstArticleAt().minus(window),
					subject.lastArticleAt().plus(window))
			.stream()
			.filter(event -> event.eventId() != subject.id())
			.flatMap(event -> score(subject, event, window).stream())
			.filter(candidate -> candidate.score() >= this.criteria.store())
			.toList();
		Map<Long, Double> known = this.judgements.find(subject.id(),
				scored.stream().map(ScoredCandidate::eventId).toList());
		List<Long> unknownInBand = scored.stream()
			.filter(candidate -> !known.containsKey(candidate.eventId())
					&& AssignmentRule.inBand(candidate.score(), this.criteria))
			.map(ScoredCandidate::eventId)
			.toList();
		Map<Long, Double> prior = unknownInBand.isEmpty() ? Map.of()
				: this.assignments.priorProbabilities(subject.id(), unknownInBand);
		return scored.stream().map(candidate -> {
			@Nullable Double probability = known.containsKey(candidate.eventId()) ? known.get(candidate.eventId())
					: MergeRule.priorProbability(prior.get(candidate.eventId()), this.criteria);
			return new StoredCandidate(candidate.eventId(), candidate.score(), probability);
		}).toList();
	}

	/** 쌍의 점수 — 핵심 말 겹침의 기준 쪽을 {@link MergeRule#termsFromSubject} 로 정해, 어느 쪽에서 보아도 같은 점수다 */
	private static Optional<ScoredCandidate> score(MergeSubject subject, EventSnapshot other, Duration window) {
		boolean fromSubject = MergeRule.termsFromSubject(subject.articleCount(), subject.id(), other.articleCount(),
				other.eventId());
		EventSnapshot against = fromSubject ? other
				: new EventSnapshot(other.eventId(), other.cosine(), subject.keyTerms(), other.firstArticleAt(),
						other.lastArticleAt(), other.articleCount());
		List<String> terms = fromSubject ? subject.keyTerms() : other.keyTerms();
		return CandidateScoring.score(against, terms, subject.firstArticleAt(), subject.lastArticleAt(), window);
	}

	/**
	 * 띠의 후보를 사건 소속 판정에 묻는다 — 트랜잭션 밖. 판정할 기사는 바뀐 사건의 서술 기사 하나다. 묻지 못하면(서술 기사 없음) 판정 뒤 규칙으로
	 * 정한다.
	 */
	private Resolution judge(MergeSubject subject, Decision.Ask ask, List<StoredCandidate> stored,
			Instant now) {
		List<ArticleId> own = this.events.describers(subject.id(), subject.modelVersion(), 1);
		List<EventDescriptor> descriptors = new ArrayList<>();
		for (long eventId : ask.eventIds()) {
			List<ArticleId> describers = this.events.describers(eventId, subject.modelVersion(),
					AssignmentSchedule.DESCRIBED_ARTICLES);
			if (!describers.isEmpty()) {
				descriptors.add(new EventDescriptor(eventId, describers));
			}
		}
		if (own.isEmpty() || descriptors.isEmpty()) {
			return new Resolution.Decided(AssignmentRule.decide(stored, this.criteria, DecisionPhase.AFTER_JUDGEMENT));
		}
		return switch (this.membership.judge(new MembershipQuery(own.get(0), descriptors))) {
			case MembershipJudgement.Judged judged -> {
				this.transaction.executeWithoutResult(
						status -> this.judgements.save(subject.id(), judged.probabilities(), judged.judgement(), now));
				List<StoredCandidate> judgedStored = stored.stream()
					.map(candidate -> new StoredCandidate(candidate.eventId(), candidate.score(),
							judged.probabilities().getOrDefault(candidate.eventId(), candidate.probability())))
					.toList();
				yield new Resolution.Decided(
						AssignmentRule.decide(judgedStored, this.criteria, DecisionPhase.AFTER_JUDGEMENT));
			}
			case MembershipJudgement.Failed failed -> switch (failed.kind()) {
				case TRANSIENT -> {
					logger.info("사건 병합 판정이 일시 실패해 다음 회차에 다시 본다 — 사건 {}: {}", subject.id(),
							failed.message());
					yield new Resolution.Later();
				}
				case ARTICLE_REJECTED -> {
					logger.warn("평가기가 기사를 거부해 병합 판정을 확률 없이 정한다 — 사건 {}: {}", subject.id(),
							failed.message());
					yield new Resolution.Decided(
							AssignmentRule.decide(stored, this.criteria, DecisionPhase.AFTER_JUDGEMENT));
				}
				case EVALUATOR_UNAVAILABLE -> new Resolution.Halt(failed.message());
			};
			case MembershipJudgement.ArticleMissing missing -> new Resolution.Later();
		};
	}

	/**
	 * 두 사건을 합친다 — 한 트랜잭션에서 배정 행 → 사건을 잠그고, 둘 다 열려 있고 모델 버전이 같고 잠근 배정 수가 사건의 기사 수와 같을 때만.
	 * @return 합쳤으면 남는 사건과 합쳐진 사건
	 */
	private Optional<Step.Merged> merge(long subjectId, long otherId, Instant now) {
		Step.@Nullable Merged done = this.transaction.execute(status -> {
			List<Long> pair = List.of(subjectId, otherId);
			List<EventArticle> rows = this.assignments.lockByEvents(pair);
			Map<Long, LockedEvent> locked = this.events.lockAll(pair)
				.stream()
				.collect(Collectors.toMap(LockedEvent::id, Function.identity()));
			@Nullable LockedEvent a = locked.get(subjectId);
			@Nullable LockedEvent b = locked.get(otherId);
			if (a == null || b == null || a.status() != EventStatus.OPEN || b.status() != EventStatus.OPEN
					|| !a.modelVersion().equals(b.modelVersion()) || members(rows, a) != a.articleCount()
					|| members(rows, b) != b.articleCount()) {
				status.setRollbackOnly();
				return null;
			}
			LockedEvent survivor = (MergeRule.survivor(a, b) == a.id()) ? a : b;
			LockedEvent absorbed = (survivor == a) ? b : a;
			this.assignments.moveAll(absorbed.id(), survivor.id());
			this.events.absorb(survivor.absorbed(absorbed), absorbed, now);
			return new Step.Merged(survivor.id(), absorbed.id());
		});
		return Optional.ofNullable(done);
	}

	private static long members(List<EventArticle> rows, LockedEvent event) {
		return rows.stream().filter(row -> row.eventId() == event.id()).count();
	}

	/** 사건 하나를 본 뒤 회차가 할 일 */
	private sealed interface Step {

		/** 다음 사건으로 */
		record Next() implements Step {
		}

		/** 합쳤다 — 두 사건은 이 회차에서 다시 보지 않는다 */
		record Merged(long survivor, long absorbed) implements Step {
		}

		/** 회차를 멈춘다 */
		record Stop(String message) implements Step {
		}

	}

	/** 판정을 마친 결과 */
	private sealed interface Resolution {

		/** 적용할 판정 */
		record Decided(Decision decision) implements Resolution {
		}

		/** 표시하지 않고 다음 회차에 다시 본다 — 일시 실패 · 판정할 기사 없음 */
		record Later() implements Resolution {
		}

		/** 평가기를 쓸 수 없다 — 회차를 멈춘다 */
		record Halt(String message) implements Resolution {
		}

	}

}
