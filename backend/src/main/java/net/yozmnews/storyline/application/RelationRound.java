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
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.FlowDescriptor;
import net.yozmnews.evaluation.FlowHappening;
import net.yozmnews.evaluation.FlowRelation;
import net.yozmnews.evaluation.RelationJudgement;
import net.yozmnews.evaluation.RelationQuery;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventProfile;
import net.yozmnews.event.EventTermCounts;
import net.yozmnews.event.EventView;
import net.yozmnews.storyline.LinkedBy;
import net.yozmnews.storyline.RelationOutcome;
import net.yozmnews.storyline.RetiredStoryline;
import net.yozmnews.storyline.StorylinesRetired;
import net.yozmnews.storyline.domain.linking.AskedRecord;
import net.yozmnews.storyline.domain.linking.Compared;
import net.yozmnews.storyline.domain.linking.Cosine;
import net.yozmnews.storyline.domain.linking.LinkingAskedRepository;
import net.yozmnews.storyline.domain.linking.LinkingCriteria;
import net.yozmnews.storyline.domain.linking.LinkingCriteriaRepository;
import net.yozmnews.storyline.domain.linking.LinkingScoring;
import net.yozmnews.storyline.domain.linking.OpeningSelection;
import net.yozmnews.storyline.domain.linking.Representation;
import net.yozmnews.storyline.domain.linking.Scored;
import net.yozmnews.storyline.domain.linking.Subject;
import net.yozmnews.storyline.domain.linking.TermWeights;
import net.yozmnews.storyline.domain.relation.FragmentMerge;
import net.yozmnews.storyline.domain.relation.GroupedBy;
import net.yozmnews.storyline.domain.relation.Grouping;
import net.yozmnews.storyline.domain.relation.PendingStoryline;
import net.yozmnews.storyline.domain.relation.Relation;
import net.yozmnews.storyline.domain.relation.RelationCandidates;
import net.yozmnews.storyline.domain.relation.RelationCriteria;
import net.yozmnews.storyline.domain.relation.RelationDescription;
import net.yozmnews.storyline.domain.relation.RelationGraph;
import net.yozmnews.storyline.domain.relation.RelationKind;
import net.yozmnews.storyline.domain.relation.RelationQueueRepository;
import net.yozmnews.storyline.domain.relation.RelationRepository;
import net.yozmnews.storyline.domain.relation.RelationRule;
import net.yozmnews.storyline.domain.relation.RelationSchedule;
import net.yozmnews.storyline.domain.storyline.LinkJudgement;
import net.yozmnews.storyline.domain.storyline.MergeBasis;
import net.yozmnews.storyline.domain.storyline.StorylineProfile;
import net.yozmnews.storyline.domain.storyline.StorylineRepository;
import net.yozmnews.storyline.domain.storyline.StorylineSnapshot;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
 * <p>
 * 답이 같은 사안의 조각이면 확률이 합치기 확률 기준 이상일 때 두 스토리라인을 하나로 합치고({@link FragmentMerge} ·
 * {@link StorylineMerger}), 미만이면 관계를 맺지 않고 둘로 둔다. 합치면 그 스토리라인의 남은 쌍은 이 회차에 묻지 않는다.
 * <p>
 * 답이 나란한 흐름이면 두 스토리라인을 한 상위 아래 묶는다({@link Grouping}). 둘 다 상위가 없으면 반영 전에 트랜잭션 밖에서 두
 * 흐름의 사건을 평가하고({@link GroupOpening}), 반영 트랜잭션이 상태를 다시 확인한 뒤 통과한 사건으로 상위를 연다. 한쪽에만 상위가
 * 있으면 그 상위와 다른 흐름의 쌍을 넘겨 물을 쌍으로 둔다. 나란한 흐름으로 묶였던 쌍의 답이 바뀌면 그 쌍이 맺은 고리를 지우지 않고 그
 * 고리의 두 끝을 넘겨 묻는다.
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

	private final GroupOpening opening;

	private final StorylineMerger merger;

	private final ApplicationEventPublisher publisher;

	private final LinkingCriteria linkingCriteria;

	private final LinkingCriteriaRepository linkingCriteriaHistory;

	private final LinkingAskedRepository linkAsked;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	RelationRound(RelationQueueRepository queue, RelationRepository relations, StorylineRepository storylines,
			EventDirectory events, FlowRelation judge, RelationCriteria criteria, GroupOpening opening,
			StorylineMerger merger, ApplicationEventPublisher publisher, LinkingCriteria linkingCriteria,
			LinkingCriteriaRepository linkingCriteriaHistory, LinkingAskedRepository linkAsked,
			TransactionTemplate transaction) {
		this(queue, relations, storylines, events, judge, criteria, opening, merger, publisher, linkingCriteria,
				linkingCriteriaHistory, linkAsked, transaction, InstantSource.system());
	}

	/** 시험이 시계를 넣는다 */
	RelationRound(RelationQueueRepository queue, RelationRepository relations, StorylineRepository storylines,
			EventDirectory events, FlowRelation judge, RelationCriteria criteria, GroupOpening opening,
			StorylineMerger merger, ApplicationEventPublisher publisher, LinkingCriteria linkingCriteria,
			LinkingCriteriaRepository linkingCriteriaHistory, LinkingAskedRepository linkAsked,
			TransactionTemplate transaction, InstantSource clock) {
		this.queue = queue;
		this.relations = relations;
		this.storylines = storylines;
		this.events = events;
		this.judge = judge;
		this.criteria = criteria;
		this.opening = opening;
		this.merger = merger;
		this.publisher = publisher;
		this.linkingCriteria = linkingCriteria;
		this.linkingCriteriaHistory = linkingCriteriaHistory;
		this.linkAsked = linkAsked;
		this.transaction = transaction;
		this.clock = clock;
	}

	/** 회차 하나를 돈다 */
	public void run() {
		Instant now = this.clock.instant();
		// 상위를 열 때의 평가는 잇기 기준으로 한다 — 그 소속이 가리킬 잇기 기준 이력도 함께 등록한다
		Criteria criteriaIds = Objects.requireNonNull(this.transaction
			.execute(status -> new Criteria(this.relations.registerCriteria(this.criteria, now),
					this.linkingCriteriaHistory.register(this.linkingCriteria, now))));
		Map<Long, TermWeights> weights = new HashMap<>();
		for (PendingStoryline claim : claim(now)) {
			if (relate(claim, criteriaIds, weights) == Next.STOP) {
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
	private Next relate(PendingStoryline claim, Criteria criteriaIds, Map<Long, TermWeights> weights) {
		long criteriaId = criteriaIds.relation();
		long storylineId = claim.storylineId();
		UUID token = Objects.requireNonNull(claim.lease(), "lease").token();
		List<Pair> pairs = Objects.requireNonNull(this.transaction.execute(status -> pairs(storylineId, weights)));
		for (Pair pair : pairs.stream().limit(RelationSchedule.MAX_PAIRS).toList()) {
			Optional<RelationQuery> query = query(pair);
			RelationJudgement judgement = query.isPresent() ? this.judge.judge(query.get())
					: new RelationJudgement.NothingToAsk();
			Next next = switch (judgement) {
				case RelationJudgement.Judged judged -> switch (evaluate(pair, judged)) {
					case Opening.Unneeded unneeded ->
						reflect(pair, judged, null, criteriaIds) ? merged(storylineId, token) : Next.PAIR;
					case Opening.Evaluated evaluated ->
						reflect(pair, judged, evaluated.passed(), criteriaIds) ? merged(storylineId, token) : Next.PAIR;
					case Opening.Transient failed -> {
						fail(storylineId, token);
						yield Next.STORYLINE;
					}
					case Opening.Unavailable unavailable -> {
						logger.error("스토리라인 관계 판정을 멈췄다 — 평가기를 쓸 수 없다: {}", unavailable.message());
						yield Next.STOP;
					}
				};
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
			// 물을 수 없는 스토리라인의 넘김은 남기지 않는다 — 기록된 결말이 그대로 선다
			this.relations.clearReferrals(storylineId);
			return List.of();
		}
		StorylineProfile own = found.get();
		Map<Long, Integer> asked = this.relations.askedAtSmallerEvents(storylineId);
		Map<Long, StorylineProfile> profiles = new HashMap<>();
		Map<Long, Integer> eventCounts = new HashMap<>();
		Set<Long> referred = this.relations.referred(storylineId);
		Set<Long> grouped = this.relations.groupedPartners(storylineId);
		for (long other : referred) {
			if (comparable(own, other, profiles)) {
				eventCounts.put(other, profiles.get(other).events());
			}
		}
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
		List<Long> partners = RelationCandidates.partners(storylineId, own.events(), sharing, scored, eventCounts, asked,
				referred, grouped, this.criteria);
		// 물을 수 없는 넘김(상대가 없거나 사건 수 하한 미만 · 다시 물을 때가 아님 · 나란한 흐름 쌍이 맺은 고리의 상대)은 지운다
		referred.stream()
			.filter(other -> !partners.contains(other))
			.forEach(other -> this.relations.clearReferral(storylineId, other));
		for (long partner : partners) {
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

	/**
	 * 반영 전에 상위를 열 평가가 필요한지 보고, 필요하면 트랜잭션 밖에서 평가한다 — 답이 나란한 흐름이고 두 스토리라인 모두 상위가 없을 때다.
	 * 여기서 읽은 것은 반영 트랜잭션이 다시 확인한다
	 */
	private Opening evaluate(Pair pair, RelationJudgement.Judged judged) {
		long first = pair.first().id();
		long second = pair.second().id();
		boolean needed = Boolean.TRUE.equals(this.transaction.execute(status -> !gone(first) && !gone(second)
				&& decide(first, second, judged) instanceof RelationRule.Decision.Parallel
				&& grouping(first, second) instanceof Grouping.Action.Open));
		if (!needed) {
			return new Opening.Unneeded();
		}
		return switch (this.opening.evaluate(first, second, this.linkingCriteria)) {
			case GroupOpening.Evaluated.Passed passed -> new Opening.Evaluated(passed);
			case GroupOpening.Evaluated.Transient failed -> new Opening.Transient();
			case GroupOpening.Evaluated.Unavailable unavailable -> new Opening.Unavailable(unavailable.message());
		};
	}

	/**
	 * 판정(storyline-R29) — 구조 제약은 이 쌍 사이의 관계와 이 쌍이 나란한 흐름으로 묶일 때 맺은 고리를 뺀 관계들로 본다. 이 쌍이 맺어 둔
	 * 고리가 이 쌍의 새 판정을 막지 않게 한다
	 */
	private RelationRule.Decision decide(long first, long second, RelationJudgement.Judged judged) {
		List<Relation> own = this.relations.groupedBy(first, second);
		List<Relation> others = this.relations.all().stream().filter(relation -> !own.contains(relation)).toList();
		return RelationRule.decide(first, second, judged.probabilities(), judged.firstOneMatter(),
				judged.secondOneMatter(), new RelationGraph(others), this.criteria);
	}

	/** 나란한 흐름으로 판정된 쌍을 어떻게 묶는가 — 이 쌍 사이의 관계를 뺀 지금 관계로 본다 */
	private Grouping.Action grouping(long first, long second) {
		return Grouping.decide(first, second, new RelationGraph(this.relations.all()).without(first, second));
	}

	/**
	 * 두 스토리라인이 합쳐졌다 — 이 스토리라인의 남은 쌍은 합치기 전의 구성 사건으로 고른 것이라 묻지 않는다. 점유를 풀고 다음
	 * 스토리라인으로 간다. 남는 스토리라인은 관계 대기에 들어 있어 다음 회차가 지금 구성으로 후보를 다시 고른다
	 */
	private Next merged(long storylineId, UUID token) {
		this.transaction.executeWithoutResult(status -> this.queue.releaseIfLeased(storylineId, token));
		return Next.STORYLINE;
	}

	/**
	 * 판정을 반영한다(storyline-R29 · R30) — 모듈 잠금 안에서 두 스토리라인을 다시 읽고, 없어졌으면 버린다.
	 * @param evaluated 반영 전에 한 상위 열기 평가 — 하지 않았으면 null
	 * @return 두 스토리라인을 합쳤는가
	 */
	private boolean reflect(Pair pair, RelationJudgement.Judged judged,
			GroupOpening.Evaluated.@Nullable Passed evaluated, Criteria criteriaIds) {
		long first = pair.first().id();
		long second = pair.second().id();
		long judgementId = judged.judgement().value();
		return Boolean.TRUE.equals(this.transaction.execute(status -> {
			this.storylines.lockLinking();
			if (gone(first) || gone(second)) {
				return false;
			}
			Instant now = this.clock.instant();
			RelationOutcome outcome;
			switch (decide(first, second, judged)) {
				case RelationRule.Decision.Link link -> {
					overturn(first, second, link, now);
					this.relations.link(link.relation(), link.probability(), judgementId, criteriaIds.relation(), now);
					outcome = RelationOutcome.LINKED;
				}
				case RelationRule.Decision.None none -> {
					overturn(first, second, none, now);
					this.relations.unlink(first, second);
					outcome = RelationRule.outcomeOf(none);
				}
				case RelationRule.Decision.Fragments fragments -> {
					if (FragmentMerge.decide(fragments, this.criteria) instanceof FragmentMerge.Action.Merge merge) {
						// 합친 쌍은 한쪽이 사라져 물은 기록을 남기지 않는다 — 근거는 합쳐진 행이 갖는다(storyline-R18)
						RetiredStoryline retired = this.merger.merge(merge.merged(), merge.survivor(),
								new MergeBasis(judgementId, criteriaIds.relation()), now);
						this.queue.enqueue(merge.survivor(), now);
						this.publisher.publishEvent(new StorylinesRetired(List.of(retired)));
						return true;
					}
					overturn(first, second, fragments, now);
					this.relations.unlink(first, second);
					outcome = RelationOutcome.UNMERGED;
				}
				case RelationRule.Decision.Parallel parallel -> {
					Grouping.Action action = grouping(first, second);
					if (action instanceof Grouping.Action.Open && evaluated == null) {
						// 평가한 뒤 상태가 바뀌어 이제야 열 차례가 됐다 — 아무것도 쓰지 않고 다음 회차가 다시 묻게 한다
						this.queue.enqueue(first, now);
						this.queue.enqueue(second, now);
						return false;
					}
					this.relations.unlink(first, second);
					boolean opened = false;
					switch (action) {
						case Grouping.Action.Open open ->
							opened = open(first, second, parallel, evaluated, judgementId, criteriaIds, now);
						case Grouping.Action.Refer refer -> refer(refer.parent(), refer.other(), now);
						case Grouping.Action.Already already -> {
						}
						case Grouping.Action.Reject reject -> {
						}
					}
					outcome = Grouping.outcomeOf(action, opened);
				}
			}
			this.relations.recordAsked(first, second, pair.smallerEvents(), judgementId, criteriaIds.relation(), outcome,
					now);
			this.relations.clearReferral(first, second);
			return false;
		}));
	}

	/**
	 * 나란한 흐름으로 묶였던 쌍의 답이 나란한 흐름이 아니게 됐다 — 그 쌍이 맺은 고리를 {@link Grouping#overturn} 대로 처분한다. 지울 고리를 먼저
	 * 지운다(하위의 상위는 하나다). 표지를 푼 고리는 그 두 끝을 넘겨 물을 쌍으로 두고 상위를 관계 대기에 넣는다
	 */
	private void overturn(long first, long second, RelationRule.Decision decision, Instant now) {
		List<Relation> own = this.relations.groupedBy(first, second);
		if (own.isEmpty()) {
			return;
		}
		Grouping.Overturn overturn = Grouping.overturn(decision, own);
		for (Relation removed : overturn.removed()) {
			this.relations.unlink(removed.sourceId(), removed.targetId());
		}
		for (Relation released : overturn.released()) {
			this.relations.regroup(released, null);
			refer(released.sourceId(), released.targetId(), now);
		}
	}

	/** 그 쌍을 넘겨 물을 쌍으로 두고 두 스토리라인을 관계 대기에 넣는다 */
	private void refer(long a, long b, Instant now) {
		this.relations.refer(a, b, now);
		this.queue.enqueue(a, now);
		this.queue.enqueue(b, now);
	}

	/**
	 * 평가를 통과한 사건으로 상위를 열고 두 흐름을 그 하위로 둔다 — 통과한 사건 가운데 지금도 두 흐름의 구성 사건이고 프로필이 읽히는
	 * 것만 넣는다. 열 수 없으면({@link OpeningSelection#openable}) 아무것도 쓰지 않는다.
	 * @return 열었는가
	 */
	private boolean open(long first, long second, RelationRule.Decision.Parallel parallel,
			GroupOpening.Evaluated.Passed evaluated, long judgementId, Criteria criteriaIds, Instant now) {
		Map<Long, List<Long>> members = this.storylines.members(List.of(first, second));
		Set<Long> firstMembers = new TreeSet<>(members.getOrDefault(first, List.of()));
		Set<Long> secondMembers = new TreeSet<>(members.getOrDefault(second, List.of()));
		List<GroupOpening.Evaluated.Member> candidates = evaluated.members()
			.stream()
			.filter(member -> firstMembers.contains(member.eventId()) || secondMembers.contains(member.eventId()))
			.toList();
		Map<EventId, EventProfile> profiles = this.events
			.profiles(candidates.stream().map(member -> new EventId(member.eventId())).toList(), LinkingScoring.KEY_TERMS);
		List<GroupOpening.Evaluated.Member> kept = candidates.stream()
			.filter(member -> profiles.containsKey(new EventId(member.eventId())))
			.toList();
		Set<Long> keptIds = new TreeSet<>(kept.stream().map(GroupOpening.Evaluated.Member::eventId).toList());
		if (!OpeningSelection.openable(keptIds, firstMembers, secondMembers)) {
			return false;
		}
		long group = evaluated.reservedId();
		this.storylines.open(group,
				Representation.of(kept.stream().map(member -> profiles.get(new EventId(member.eventId()))).toList()), now);
		for (GroupOpening.Evaluated.Member member : kept) {
			this.storylines.addMember(group, member.eventId(),
					LinkJudgement.judged(LinkedBy.GROUP, member.score(), criteriaIds.linking(), member.asked(), now));
		}
		// 띠 안에서 물은 결과는 든 사건 · 들지 못한 사건 모두 남긴다 — 잇기가 같은 사건을 이 상위에 곧바로 다시 묻지 않는다(storyline-R36)
		evaluated.asked().forEach((event, asked) -> {
			if (firstMembers.contains(event) || secondMembers.contains(event)) {
				this.linkAsked.recordLink(event,
						Map.of(group, new AskedRecord(asked.asked(), asked.eventArticles(), kept.size())), now);
			}
		});
		GroupedBy by = GroupedBy.of(first, second);
		this.relations.linkGrouped(new Relation(group, first, RelationKind.CONTAINS), by, parallel.probability(),
				judgementId, criteriaIds.relation(), now);
		this.relations.linkGrouped(new Relation(group, second, RelationKind.CONTAINS), by, parallel.probability(),
				judgementId, criteriaIds.relation(), now);
		this.queue.enqueue(group, now);
		return true;
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
			this.relations.clearReferral(first, second);
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

	/** 회차가 판정에 쓴 기준의 이력 ID — 관계 기준과, 상위를 열 때의 평가에 쓴 잇기 기준 */
	private record Criteria(long relation, long linking) {
	}

	/** 반영 전에 한 상위 열기 평가 */
	private sealed interface Opening {

		/** 평가할 일이 없다 */
		record Unneeded() implements Opening {
		}

		record Evaluated(GroupOpening.Evaluated.Passed passed) implements Opening {
		}

		/** 평가기의 일시 실패 */
		record Transient() implements Opening {
		}

		/** 평가기를 쓸 수 없다 */
		record Unavailable(String message) implements Opening {
		}

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
