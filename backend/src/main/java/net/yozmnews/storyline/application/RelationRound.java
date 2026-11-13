package net.yozmnews.storyline.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.FlowDescriptor;
import net.yozmnews.evaluation.FlowHappening;
import net.yozmnews.evaluation.FlowRelation;
import net.yozmnews.evaluation.RelationJudgement;
import net.yozmnews.evaluation.RelationQuery;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventTermCounts;
import net.yozmnews.event.EventView;
import net.yozmnews.storyline.domain.linking.Compared;
import net.yozmnews.storyline.domain.linking.Cosine;
import net.yozmnews.storyline.domain.linking.LinkingScoring;
import net.yozmnews.storyline.domain.linking.Scored;
import net.yozmnews.storyline.domain.linking.Subject;
import net.yozmnews.storyline.domain.linking.TermWeights;
import net.yozmnews.storyline.domain.relation.PendingStoryline;
import net.yozmnews.storyline.domain.relation.RelationCandidates;
import net.yozmnews.storyline.domain.relation.RelationCriteria;
import net.yozmnews.storyline.domain.relation.RelationDescription;
import net.yozmnews.storyline.domain.relation.RelationGraph;
import net.yozmnews.storyline.domain.relation.RelationOutcome;
import net.yozmnews.storyline.domain.relation.RelationQueueRepository;
import net.yozmnews.storyline.domain.relation.RelationRepository;
import net.yozmnews.storyline.domain.relation.RelationRule;
import net.yozmnews.storyline.domain.relation.RelationSchedule;
import net.yozmnews.storyline.domain.storyline.StorylineProfile;
import net.yozmnews.storyline.domain.storyline.StorylineRepository;
import net.yozmnews.storyline.domain.storyline.StorylineSnapshot;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관계 회차 하나 — docs/specs/storyline.md §4 · storyline-R27 ~ storyline-R31.
 * <p>
 * 지금 기준을 관계 기준 이력에 등록하고 관계 대기를 스토리라인 ID 순으로 점유해 커밋한 뒤, 스토리라인마다 후보 쌍을 골라 순서대로 묻는다.
 * 관계 판정은 트랜잭션 밖이고, 결과는 쌍마다 모듈 잠금을 잡은 짧은 트랜잭션에서 두 스토리라인을 다시 읽어 반영한다. 후보를 모두 물었고 대기
 * 행이 그대로면 지우고, 아니면 점유만 푼다. 일시 실패면 그 스토리라인의 실패 수를 올리고 다음 스토리라인으로 간다. 평가기를 쓸 수 없으면
 * 회차를 멈추고, 예상 못한 예외는 밖으로 나간다 — 점유는 만료로 풀린다. 판단은 {@code domain/relation} 이 한다. 트랜잭션 · 역할
 * 애너테이션이 없다(두 역할 공통).
 */
@Service
public class RelationRound {

	private static final Logger logger = LoggerFactory.getLogger(RelationRound.class);

	private final RelationQueueRepository queue;

	private final RelationRepository relations;

	private final StorylineRepository storylines;

	private final EventDirectory events;

	private final FlowRelation judge;

	private final RelationCriteria criteria;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	RelationRound(RelationQueueRepository queue, RelationRepository relations, StorylineRepository storylines,
			EventDirectory events, FlowRelation judge, RelationCriteria criteria, TransactionTemplate transaction) {
		this(queue, relations, storylines, events, judge, criteria, transaction, InstantSource.system());
	}

	/** 시험이 시계를 넣는다 */
	RelationRound(RelationQueueRepository queue, RelationRepository relations, StorylineRepository storylines,
			EventDirectory events, FlowRelation judge, RelationCriteria criteria, TransactionTemplate transaction,
			InstantSource clock) {
		this.queue = queue;
		this.relations = relations;
		this.storylines = storylines;
		this.events = events;
		this.judge = judge;
		this.criteria = criteria;
		this.transaction = transaction;
		this.clock = clock;
	}

	/** 회차 하나를 돈다 */
	public void run() {
		Instant now = this.clock.instant();
		long criteriaId = Objects
			.requireNonNull(this.transaction.execute(status -> this.relations.registerCriteria(this.criteria, now)));
		Map<Long, TermWeights> weights = new HashMap<>();
		for (PendingStoryline claim : claim(now)) {
			if (relate(claim, criteriaId, weights) == Next.STOP) {
				return;
			}
		}
	}

	private List<PendingStoryline> claim(Instant now) {
		return Objects.requireNonNull(this.transaction.execute(status -> {
			List<PendingStoryline> leased = new ArrayList<>();
			for (PendingStoryline due : this.queue.lockDue(now, RelationSchedule.BATCH_SIZE)) {
				PendingStoryline next = due.claim(UUID.randomUUID(), now);
				this.queue.saveLocked(next);
				leased.add(next);
			}
			return leased;
		}));
	}

	/** 스토리라인 하나의 후보 쌍을 묻고 반영한다 */
	private Next relate(PendingStoryline claim, long criteriaId, Map<Long, TermWeights> weights) {
		long storylineId = claim.storylineId();
		UUID token = Objects.requireNonNull(claim.lease(), "lease").token();
		List<Pair> pairs = Objects.requireNonNull(this.transaction.execute(status -> pairs(storylineId, weights)));
		for (Pair pair : pairs.stream().limit(RelationSchedule.MAX_PAIRS).toList()) {
			Optional<RelationQuery> query = query(pair);
			RelationJudgement judgement = query.isPresent() ? this.judge.judge(query.get())
					: new RelationJudgement.NothingToAsk();
			Next next = switch (judgement) {
				case RelationJudgement.Judged judged -> {
					reflect(pair, judged, criteriaId);
					yield Next.PAIR;
				}
				case RelationJudgement.NothingToAsk nothing -> {
					record(pair, RelationOutcome.NOTHING_TO_ASK, criteriaId);
					yield Next.PAIR;
				}
				case RelationJudgement.Failed failed -> switch (failed.kind()) {
					case ARTICLE_REJECTED -> {
						record(pair, RelationOutcome.REJECTED, criteriaId);
						yield Next.PAIR;
					}
					case TRANSIENT -> {
						fail(storylineId, token);
						yield Next.STORYLINE;
					}
					case EVALUATOR_UNAVAILABLE -> {
						logger.error("스토리라인 관계 판정을 멈췄다 — 평가기를 쓸 수 없다: {}", failed.message());
						yield Next.STOP;
					}
				};
			};
			if (next != Next.PAIR) {
				return next;
			}
		}
		boolean all = pairs.size() <= RelationSchedule.MAX_PAIRS;
		this.transaction.executeWithoutResult(status -> {
			if (!all || !this.queue.deleteIfUnchanged(storylineId, token, claim.notifications())) {
				// 후보가 남았거나 처리 중에 다시 들어왔다 — 다음 회차가 잇는다(storyline-R31)
				this.queue.releaseIfLeased(storylineId, token);
			}
		});
		return Next.STORYLINE;
	}

	/**
	 * 후보 쌍(storyline-R27) — 사건을 함께 가진 스토리라인과 점수 후보에서 {@link RelationCandidates#partners} 가 고른 순서대로. 점수는
	 * 물을 수 있는 상대({@link RelationCandidates#askable})에만 매긴다 — 코사인 하한을 넘는 스토리라인이 많아도 말 수를 묻는 것은 그
	 * 상대뿐이다. 트랜잭션 안에서 부른다
	 * @param weights 이 회차에서 읽은 말의 무게 — 판정할 쪽 스토리라인마다 한 번만 읽는다
	 */
	private List<Pair> pairs(long storylineId, Map<Long, TermWeights> weights) {
		Optional<StorylineProfile> found = this.storylines.profile(storylineId, LinkingScoring.KEY_TERMS);
		if (found.isEmpty() || found.get().events() < this.criteria.minEvents()) {
			return List.of();
		}
		StorylineProfile own = found.get();
		Map<Long, Integer> asked = this.relations.askedAtSmallerEvents(storylineId);
		Map<Long, StorylineProfile> profiles = new HashMap<>();
		Map<Long, Integer> eventCounts = new HashMap<>();
		Map<Long, Integer> sharing = new LinkedHashMap<>();
		for (long event : members(storylineId)) {
			for (long other : this.storylines.storylinesOf(event)) {
				if (other != storylineId && comparable(own, other, profiles)) {
					sharing.merge(other, 1, Integer::sum);
					eventCounts.put(other, profiles.get(other).events());
				}
			}
		}
		List<StorylineSnapshot> candidates = this.storylines.candidates(own.modelVersion(), own.centroid(),
				LinkingScoring.windowFrom(own.firstArticleAt(), this.criteria.window()),
				LinkingScoring.windowTo(own.lastArticleAt(), this.criteria.window()),
				LinkingScoring.cosineFloor(this.criteria.candidateScore()), LinkingScoring.KEY_TERMS);
		Map<Long, List<Long>> candidateMembers = this.storylines
			.members(candidates.stream().map(StorylineSnapshot::id).toList());
		List<Scored> scored = new ArrayList<>();
		for (StorylineSnapshot candidate : candidates) {
			long other = candidate.id();
			int otherEvents = candidateMembers.getOrDefault(other, List.of()).size();
			// 사건을 함께 가진 상대는 점수와 무관하게 후보다 — 점수는 그 밖의 물을 수 있는 상대에만 매긴다
			if (other == storylineId || sharing.containsKey(other)
					|| !RelationCandidates.askable(own.events(), otherEvents, asked.get(other), this.criteria)) {
				continue;
			}
			eventCounts.put(other, otherEvents);
			double cosine = Cosine.of(own.centroid(), candidate.centroid());
			// 구성 사건 수가 적은 쪽을 판정할 쪽으로 둔다 — 말의 무게는 판정할 쪽의 구간으로 센다(storyline-R27)
			Subject ownSide = new Subject(own.centroid(), own.firstArticleAt(), own.lastArticleAt(), own.keyTerms());
			Subject otherSide = new Subject(candidate.centroid(), candidate.firstArticleAt(), candidate.lastArticleAt(),
					candidate.keyTerms());
			boolean ownIsSubject = RelationCandidates.subjectIs(storylineId, own.events(), other, otherEvents);
			Subject subject = ownIsSubject ? ownSide : otherSide;
			Subject against = ownIsSubject ? otherSide : ownSide;
			long subjectId = ownIsSubject ? storylineId : other;
			long againstId = ownIsSubject ? other : storylineId;
			TermWeights subjectWeights = weights.computeIfAbsent(subjectId, id -> weights(subject));
			LinkingScoring
				.score(subject, new Compared(againstId, cosine, against.firstArticleAt(), against.lastArticleAt(),
						against.keyTerms()), this.criteria.window(), subjectWeights)
				.ifPresent(score -> scored.add(new Scored(other, score)));
		}
		List<Pair> pairs = new ArrayList<>();
		for (long partner : RelationCandidates.partners(storylineId, own.events(), sharing, scored, eventCounts, asked,
				this.criteria)) {
			if (!comparable(own, partner, profiles)) {
				continue;
			}
			StorylineProfile other = profiles.get(partner);
			boolean ownFirst = RelationCandidates.firstIs(storylineId, own.firstArticleAt(), partner,
					other.firstArticleAt());
			pairs.add(ownFirst ? new Pair(own, other, members(storylineId), members(partner))
					: new Pair(other, own, members(partner), members(storylineId)));
		}
		return pairs;
	}

	/** 그 스토리라인이 남아 있고 모델 버전이 같은가 — 읽은 표현은 모아 둔다 */
	private boolean comparable(StorylineProfile own, long other, Map<Long, StorylineProfile> profiles) {
		if (!profiles.containsKey(other)) {
			this.storylines.profile(other, LinkingScoring.KEY_TERMS).ifPresent(profile -> profiles.put(other, profile));
		}
		StorylineProfile profile = profiles.get(other);
		return profile != null && profile.modelVersion().equals(own.modelVersion());
	}

	private List<Long> members(long storylineId) {
		return this.storylines.members(List.of(storylineId)).getOrDefault(storylineId, List.of());
	}

	/** 판정할 쪽의 말의 무게 — 그 스토리라인의 구간과 겹치는 사건으로 센 수에서(storyline-R27) */
	private TermWeights weights(Subject subject) {
		EventTermCounts counts = this.events.termCounts(subject.keyTerms(),
				LinkingScoring.windowFrom(subject.firstArticleAt(), this.criteria.window()),
				LinkingScoring.windowTo(subject.lastArticleAt(), this.criteria.window()));
		return TermWeights.of(counts.events(), counts.eventsByTerm());
	}

	/** 관계 판정 요청(storyline-R28) — 한쪽이라도 실을 서술이 없으면 빈 값 */
	private Optional<RelationQuery> query(Pair pair) {
		Optional<FlowDescriptor> first = flow(pair.first(), pair.firstMembers());
		Optional<FlowDescriptor> second = flow(pair.second(), pair.secondMembers());
		if (first.isEmpty() || second.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new RelationQuery(first.get(), second.get()));
	}

	/** 흐름 하나 — 직접 소속 사건에서 서술 사건을 고르고 그 대표 기사를 싣는다. 대표 기사가 없는 사건은 뺀다 */
	private Optional<FlowDescriptor> flow(StorylineProfile profile, List<Long> members) {
		Map<EventId, EventView> views = this.events.views(members.stream().map(EventId::new).toList());
		List<RelationDescription.Described> described = RelationDescription.of(List.copyOf(views.values()));
		Map<EventId, ArticleId> representatives = this.events
			.representativesOfAll(described.stream().map(one -> new EventId(one.eventId())).toList());
		List<FlowHappening> happenings = new ArrayList<>();
		for (RelationDescription.Described one : described) {
			ArticleId article = representatives.get(new EventId(one.eventId()));
			if (article != null) {
				happenings.add(new FlowHappening(article, one.at(), one.summarized()));
			}
		}
		if (happenings.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new FlowDescriptor(profile.id(), profile.events(), profile.firstArticleAt(),
				profile.lastArticleAt(), happenings));
	}

	/** 판정을 반영한다(storyline-R29 · R30) — 모듈 잠금 안에서 두 스토리라인을 다시 읽고, 없어졌으면 버린다 */
	private void reflect(Pair pair, RelationJudgement.Judged judged, long criteriaId) {
		long first = pair.first().id();
		long second = pair.second().id();
		this.transaction.executeWithoutResult(status -> {
			this.storylines.lockLinking();
			if (gone(first) || gone(second)) {
				return;
			}
			Instant now = this.clock.instant();
			RelationRule.Decision decision = RelationRule.decide(first, second, judged.probabilities(),
					judged.firstOneMatter(), judged.secondOneMatter(), new RelationGraph(this.relations.all()),
					this.criteria);
			switch (decision) {
				case RelationRule.Decision.Link link -> this.relations.link(link.relation(), link.probability(),
						judged.judgement().value(), criteriaId, now);
				case RelationRule.Decision.None none -> this.relations.unlink(first, second);
			}
			this.relations.recordAsked(first, second, pair.smallerEvents(), judged.judgement().value(), criteriaId,
					RelationOutcome.of(decision), now);
		});
	}

	/** 평가기를 부르지 않았거나 입력이 거부된 쌍의 물은 기록만 남긴다 — 관계는 건드리지 않는다(storyline-R31) */
	private void record(Pair pair, RelationOutcome outcome, long criteriaId) {
		long first = pair.first().id();
		long second = pair.second().id();
		this.transaction.executeWithoutResult(status -> {
			this.storylines.lockLinking();
			if (gone(first) || gone(second)) {
				return;
			}
			this.relations.recordAsked(first, second, pair.smallerEvents(), null, criteriaId, outcome,
					this.clock.instant());
		});
	}

	/** 그 스토리라인이 지워졌거나 합쳐졌는가 */
	private boolean gone(long storylineId) {
		return this.storylines.profile(storylineId, 1).isEmpty();
	}

	/** 실패 수를 하나 올리고 점유를 푼다 — 상한이면 대기에서 뺀다(storyline-R31) */
	private void fail(long storylineId, UUID token) {
		this.transaction.executeWithoutResult(status -> {
			OptionalInt failures = this.queue.recordFailureIfLeased(storylineId, token);
			if (failures.isPresent() && RelationSchedule.failuresExhausted(failures.getAsInt())) {
				this.queue.delete(storylineId);
				logger.error("스토리라인 관계 판정이 거듭 실패해 대기에서 뺐다 — 스토리라인 {}, 실패 {}회", storylineId,
						failures.getAsInt());
			}
		});
	}

	/** 다음에 할 것 */
	private enum Next {

		/** 이 스토리라인의 다음 쌍 */
		PAIR,

		/** 다음 스토리라인 */
		STORYLINE,

		/** 회차를 멈춘다 */
		STOP

	}

	/**
	 * 물을 쌍 — 요청의 first · second 순서로.
	 *
	 * @param firstMembers first 의 직접 소속 사건
	 * @param secondMembers second 의 직접 소속 사건
	 */
	private record Pair(StorylineProfile first, StorylineProfile second, List<Long> firstMembers,
			List<Long> secondMembers) {

		/** 물을 때 작은 쪽의 구성 사건 수 */
		int smallerEvents() {
			return Math.min(this.first.events(), this.second.events());
		}

	}

}
