package net.yozmnews.storyline.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.FlowMembershipJudgementId;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventKeyTerm;
import net.yozmnews.event.EventNeighbor;
import net.yozmnews.event.EventProfile;
import net.yozmnews.event.EventTermCounts;
import net.yozmnews.event.EventView;
import net.yozmnews.storyline.LinkedBy;
import net.yozmnews.storyline.RetiredStoryline;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylinesRetired;
import net.yozmnews.storyline.domain.linking.Asked;
import net.yozmnews.storyline.domain.linking.AskedRecord;
import net.yozmnews.storyline.domain.linking.Compared;
import net.yozmnews.storyline.domain.linking.Cosine;
import net.yozmnews.storyline.domain.linking.LinkingAsk;
import net.yozmnews.storyline.domain.linking.LinkingAskedRepository;
import net.yozmnews.storyline.domain.linking.LinkingCriteria;
import net.yozmnews.storyline.domain.linking.LinkingRule;
import net.yozmnews.storyline.domain.linking.LinkingScoring;
import net.yozmnews.storyline.domain.linking.Representation;
import net.yozmnews.storyline.domain.linking.Scored;
import net.yozmnews.storyline.domain.linking.Subject;
import net.yozmnews.storyline.domain.linking.TermWeights;
import net.yozmnews.storyline.domain.queue.LinkingQueueRepository;
import net.yozmnews.storyline.domain.queue.PendingEvent;
import net.yozmnews.storyline.domain.relation.RelationMove;
import net.yozmnews.storyline.domain.relation.RelationQueueRepository;
import net.yozmnews.storyline.domain.relation.RelationRepository;
import net.yozmnews.storyline.domain.storyline.LinkJudgement;
import net.yozmnews.storyline.domain.storyline.StorylineRepository;
import net.yozmnews.storyline.domain.storyline.StorylineSnapshot;
import net.yozmnews.storyline.domain.storyline.Tidying;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 사건 하나의 따르기 · 판정 · 정돈 · 쓰기 — docs/specs/storyline.md §4 · storyline-R5 ~ storyline-R14 · storyline-R17 ·
 * storyline-R18 · storyline-R26 · storyline-R32 · storyline-R35 · storyline-R36.
 * <p>
 * 트랜잭션 안에서 부른다. 첫 문장이 모듈 잠금이라 판정은 한 번에 하나다. 사건의 지금 상태({@link EventDirectory#find})로 지워짐 ·
 * 합쳐짐 · 그대로를 가르고, 그대로면 점수를 매겨 붙이기 · 짝을 판정한다. 띠 안 후보의 확률이 물은 기록에 모자라면 아무것도 쓰지 않고
 * 물을 것({@link LinkingOutcome.Ask})을 낸다 — 회차가 트랜잭션 밖에서 묻고 {@link #record} 로 기록한 뒤 다시 부른다(storyline-R36).
 * 판정이 정해지면 그 사건이 든 스토리라인의 표현을 다시 정하고, 소속을 쓰고, 다시 넣기 · 정돈을 한다. 판단은 {@code domain/linking} ·
 * {@code domain/storyline} 이 하고, 이 클래스는 읽기 · 쓰기를 잇는다. 스토리라인을 합칠 때는 합쳐질 쪽의 관계를 남는 쪽으로 옮기고 — 옮길
 * 계획은 {@code domain/relation} 이 정한다 — 합쳐질 쪽의 물은 기록을 지운다. 정돈에서 남은 스토리라인과 짝으로 연 스토리라인은 관계
 * 대기에 넣는다. 정돈이 합치거나 지운 스토리라인은 같은 트랜잭션에서 {@link StorylinesRetired} 로 알린다(storyline-R35).
 */
@Component
class StorylineLinker {

	private final EventDirectory events;

	private final StorylineRepository storylines;

	private final LinkingQueueRepository queue;

	private final LinkingAskedRepository asked;

	private final RelationRepository relations;

	private final RelationQueueRepository relationQueue;

	private final ApplicationEventPublisher publisher;

	StorylineLinker(EventDirectory events, StorylineRepository storylines, LinkingQueueRepository queue,
			LinkingAskedRepository asked, RelationRepository relations, RelationQueueRepository relationQueue,
			ApplicationEventPublisher publisher) {
		this.events = events;
		this.storylines = storylines;
		this.queue = queue;
		this.asked = asked;
		this.relations = relations;
		this.relationQueue = relationQueue;
		this.publisher = publisher;
	}

	/**
	 * 사건 하나의 판정을 시도한다 — 트랜잭션 안에서 부른다.
	 * @param criteriaId 판정에 쓴 기준의 기준 이력 ID
	 * @param now 소속 · 표현을 쓴 시각
	 * @return 마쳤으면 {@link LinkingOutcome.Done}, 물을 것이 있으면 아무것도 쓰지 않고 {@link LinkingOutcome.Ask}
	 */
	LinkingOutcome link(long eventId, LinkingCriteria criteria, long criteriaId, Instant now) {
		this.storylines.lockLinking();
		EventId id = new EventId(eventId);
		Optional<EventView> current = this.events.find(id);
		if (current.isEmpty()) {
			// 지워진 사건 — 물은 기록 · 소속을 지우고 그 스토리라인들을 정돈한다(storyline-R6 · R17 · R36)
			this.asked.forgetEvent(eventId);
			List<Long> owned = this.storylines.storylinesOf(eventId);
			for (long storyline : owned) {
				this.storylines.removeMember(storyline, eventId);
			}
			tidy(owned, Set.copyOf(owned), now);
			return new LinkingOutcome.Done();
		}
		long survivor = current.get().id().value();
		if (survivor != eventId) {
			this.asked.forgetEvent(eventId);
			follow(eventId, survivor, now);
			return new LinkingOutcome.Done();
		}
		return judge(id, criteria, criteriaId, now);
	}

	/**
	 * 물은 답을 기록한다 — 트랜잭션 안에서 부른다. 모듈 잠금을 잡고, 사건이 그대로일 때만 쓴다(그새 지워지거나 합쳐졌으면 쓰지 않는다).
	 * 붙이기 띠는 지금 남아 있는 스토리라인의 답만 쓴다. 답에 없는 후보는 확률 없이 기록한다 — 다시 물을 때까지 붙일 수 없다.
	 * @param judgement 판정 원본 — 확률이 있으면 있다
	 * @param probabilities 답 — 후보 ID → 확률. 입력 거부 · 물을 서술이 없음이면 비었다
	 */
	void record(LinkingOutcome.Ask ask, @Nullable FlowMembershipJudgementId judgement,
			Map<Long, Double> probabilities, Instant now) {
		this.storylines.lockLinking();
		long eventId = ask.eventId();
		Optional<EventView> current = this.events.find(new EventId(eventId));
		if (current.isEmpty() || current.get().id().value() != eventId) {
			return;
		}
		Set<Long> alive = switch (ask.target()) {
			case LINK -> this.storylines.members(ask.flows().stream().map(LinkingOutcome.AskedFlow::flowId).toList())
				.keySet();
			case PAIR -> ask.flows().stream().map(LinkingOutcome.AskedFlow::flowId).collect(Collectors.toSet());
		};
		Map<Long, AskedRecord> records = new TreeMap<>();
		for (LinkingOutcome.AskedFlow flow : ask.flows()) {
			if (!alive.contains(flow.flowId())) {
				continue;
			}
			Double probability = probabilities.get(flow.flowId());
			Asked answer = (probability == null || judgement == null) ? Asked.UNANSWERED
					: new Asked(probability, judgement.value());
			records.put(flow.flowId(), new AskedRecord(answer, ask.eventArticles(), flow.size()));
		}
		switch (ask.target()) {
			case LINK -> this.asked.recordLink(eventId, records, now);
			case PAIR -> this.asked.recordPair(eventId, records, now);
		}
	}

	/**
	 * 합쳐진 사건 — 소속을 남는 사건으로 옮기거나, 남는 사건이 이미 구성 사건이면 지운 뒤 그 스토리라인들을 정돈한다(storyline-R6 ·
	 * R17). 다시 넣기의 계기가 아니다
	 */
	private void follow(long merged, long survivor, Instant now) {
		List<Long> owned = this.storylines.storylinesOf(merged);
		Map<Long, List<Long>> members = this.storylines.members(owned);
		for (long storyline : owned) {
			if (members.getOrDefault(storyline, List.of()).contains(survivor)) {
				this.storylines.removeMember(storyline, merged);
			}
			else {
				this.storylines.moveMember(storyline, merged, survivor, now);
			}
		}
		tidy(owned, Set.copyOf(owned), now);
	}

	private LinkingOutcome judge(EventId id, LinkingCriteria criteria, long criteriaId, Instant now) {
		long eventId = id.value();
		List<Long> own = this.storylines.storylinesOf(eventId);
		Map<Long, List<Long>> members = this.storylines.members(own);
		Set<Long> needed = new LinkedHashSet<>();
		needed.add(eventId);
		members.values().forEach(needed::addAll);
		Map<EventId, EventProfile> profiles = this.events.profiles(ids(needed), LinkingScoring.KEY_TERMS);
		EventProfile profile = profiles.get(id);
		if (profile == null) {
			// 그사이 지워지거나 합쳐졌다 — 그 변화의 알림이 다시 넣는다(storyline-R6)
			return new LinkingOutcome.Done();
		}
		Subject subject = Subject.of(profile);
		double floor = LinkingScoring.cosineFloor(criteria.lower());
		Instant from = LinkingScoring.windowFrom(subject.firstArticleAt(), criteria.window());
		Instant to = LinkingScoring.windowTo(subject.lastArticleAt(), criteria.window());

		// 이미 든 스토리라인은 이 사건을 뺀 표현과 견준다(storyline-R9) — 표현을 다시 정해 쓰는 것은 판정이 정해진 뒤다
		List<Compared> compared = new ArrayList<>();
		for (long storyline : own) {
			List<EventProfile> readable = readable(members.getOrDefault(storyline, List.of()), profiles);
			Representation.without(readable, id).ifPresent(rest -> compared.add(rest.comparedTo(storyline, subject)));
		}
		Map<Long, TermWeights> weights = new TreeMap<>();
		weights.put(eventId, weights(subject, criteria));
		for (StorylineSnapshot candidate : this.storylines.candidates(profile.modelVersion(), profile.centroid(), from, to,
				floor, LinkingScoring.KEY_TERMS)) {
			if (!own.contains(candidate.id())) {
				compared.add(new Compared(candidate.id(), Cosine.of(subject.centroid(), candidate.centroid()),
						candidate.firstArticleAt(), candidate.lastArticleAt(), candidate.keyTerms()));
			}
		}
		List<Scored> scored = score(subject, compared, criteria, weights.get(eventId));

		Map<Long, List<EventNeighbor>> neighbours = new TreeMap<>();
		Map<Long, EventProfile> gained = new TreeMap<>();
		Set<Long> attached = new TreeSet<>();
		Map<Long, List<Long>> bandMembers = new TreeMap<>();
		Map<Long, Asked> usableLinks = usableLinks(eventId, profile.articleCount(), scored, criteria, bandMembers);
		switch (LinkingRule.attach(scored, Set.copyOf(own), usableLinks, criteria)) {
			case LinkingRule.Choice.Ask ask -> {
				Optional<LinkingOutcome.Ask> question = askLinks(profile, subject, ask.band(), bandMembers);
				if (question.isPresent()) {
					return question.get();
				}
				// 판정할 사건의 대표 기사가 없어 묻지 못한다 — 띠 후보는 이번 판정에서 붙일 수 없다
				represent(own, members, profiles, now);
			}
			case LinkingRule.Choice.Take take -> {
				// 붙이기(storyline-R10)
				long storyline = take.chosen().id();
				represent(own, members, profiles, now);
				this.storylines.addMember(storyline, eventId,
						LinkJudgement.judged(LinkedBy.RULE, take.chosen().score(), criteriaId, take.asked(), now));
				attached.add(storyline);
				gained.put(eventId, profile);
			}
			case LinkingRule.Choice.None none -> {
				if (own.isEmpty()) {
					// 짝(storyline-R11)
					List<EventNeighbor> near = this.events.neighbors(id, from, to, floor, LinkingScoring.KEY_TERMS,
							LinkingScoring.NEIGHBORS);
					neighbours.put(eventId, near);
					List<Scored> scoredNear = score(subject, compared(near), criteria, weights.get(eventId));
					Set<Long> affiliated = this.storylines.affiliated(neighbourIds(near));
					Map<Long, Asked> usablePairs = usablePairs(eventId, profile.articleCount(), scoredNear, near,
							criteria);
					switch (LinkingRule.pair(scoredNear, affiliated, usablePairs, criteria)) {
						case LinkingRule.Choice.Ask ask -> {
							Optional<LinkingOutcome.Ask> question = askPairs(profile, ask.band(), near);
							if (question.isPresent()) {
								return question.get();
							}
						}
						case LinkingRule.Choice.Take take -> open(id, profile, take, criteriaId, now, gained);
						case LinkingRule.Choice.None nothing -> {
						}
					}
				}
				else {
					represent(own, members, profiles, now);
				}
			}
		}
		requeue(gained, neighbours, weights, criteria, now);
		// 정돈 — 이미 든 스토리라인은 앞에서 표현을 다시 정했으므로 정돈만, 붙인 스토리라인은 표현까지(storyline-R17)
		Set<Long> tidied = new TreeSet<>(own);
		tidied.addAll(attached);
		tidy(tidied, attached, now);
		return new LinkingOutcome.Done();
	}

	/** 짝으로 새 스토리라인을 연다(storyline-R11) — 두 소속의 근거는 같다 */
	private void open(EventId id, EventProfile profile, LinkingRule.Choice.Take take, long criteriaId, Instant now,
			Map<Long, EventProfile> gained) {
		EventId partnerId = new EventId(take.chosen().id());
		EventProfile partner = this.events.profiles(List.of(partnerId), LinkingScoring.KEY_TERMS).get(partnerId);
		if (partner == null) {
			return;
		}
		long storyline = this.storylines.open(Representation.of(List.of(profile, partner)), now);
		LinkJudgement judgement = LinkJudgement.judged(LinkedBy.PAIR, take.chosen().score(), criteriaId, take.asked(),
				now);
		this.storylines.addMember(storyline, id.value(), judgement);
		this.storylines.addMember(storyline, partnerId.value(), judgement);
		// 짝으로 연 스토리라인은 정돈을 거치지 않는다 — 여기서 관계 대기에 넣는다(storyline-R26)
		this.relationQueue.enqueue(storyline, now);
		gained.put(id.value(), profile);
		gained.put(partnerId.value(), partner);
	}

	/** 이미 든 스토리라인의 표현을 지금 값으로 다시 정해 쓴다(storyline-R7) — 판정이 정해진 뒤에 부른다 */
	private void represent(List<Long> own, Map<Long, List<Long>> members, Map<EventId, EventProfile> profiles,
			Instant now) {
		for (long storyline : own) {
			List<EventProfile> readable = readable(members.getOrDefault(storyline, List.of()), profiles);
			if (!readable.isEmpty()) {
				this.storylines.save(storyline, Representation.of(readable), now);
			}
		}
	}

	/**
	 * 붙이기 띠의 쓸 수 있는 물은 기록 — 띠 안 후보({@link LinkingCriteria#inBand})의 기록 가운데 다시 물을 때가 아닌 것({@link LinkingAsk#due}).
	 * @param bandMembers 읽은 후보의 구성 사건 — 서술을 고를 때 다시 쓴다
	 */
	private Map<Long, Asked> usableLinks(long eventId, int eventArticles, List<Scored> scored, LinkingCriteria criteria,
			Map<Long, List<Long>> bandMembers) {
		List<Long> band = scored.stream()
			.filter(one -> criteria.inBand(one.score()))
			.map(Scored::id)
			.toList();
		if (band.isEmpty()) {
			return Map.of();
		}
		bandMembers.putAll(this.storylines.members(band));
		Map<Long, Asked> usable = new LinkedHashMap<>();
		this.asked.ofLink(eventId, band).forEach((storyline, record) -> {
			int size = bandMembers.getOrDefault(storyline, List.of()).size();
			if (!LinkingAsk.due(record, eventArticles, size)) {
				usable.put(storyline, record.asked());
			}
		});
		return usable;
	}

	/** 짝 띠의 쓸 수 있는 물은 기록 — 이웃의 크기는 기사 수다 */
	private Map<Long, Asked> usablePairs(long eventId, int eventArticles, List<Scored> scored,
			List<EventNeighbor> near, LinkingCriteria criteria) {
		List<Long> band = scored.stream()
			.filter(one -> criteria.inBand(one.score()))
			.map(Scored::id)
			.toList();
		if (band.isEmpty()) {
			return Map.of();
		}
		Map<Long, Integer> sizes = new TreeMap<>();
		near.forEach(neighbour -> sizes.put(neighbour.id().value(), neighbour.articleCount()));
		Map<Long, Asked> usable = new LinkedHashMap<>();
		this.asked.ofPair(eventId, band).forEach((partner, record) -> {
			if (!LinkingAsk.due(record, eventArticles, sizes.getOrDefault(partner, record.otherSize()))) {
				usable.put(partner, record.asked());
			}
		});
		return usable;
	}

	/**
	 * 붙이기 띠의 물을 것 — 후보마다 판정할 사건과 가장 가까운 구성 사건(같은 모델 버전)의 대표 기사. 판정할 사건의 대표 기사가 없으면
	 * 묻지 못한다(빈 값 — 띠 후보는 이번 판정에서 붙일 수 없다)
	 */
	private Optional<LinkingOutcome.Ask> askLinks(EventProfile profile, Subject subject, List<Scored> band,
			Map<Long, List<Long>> bandMembers) {
		Optional<ArticleId> article = representative(profile.id());
		if (article.isEmpty()) {
			return Optional.empty();
		}
		Set<Long> memberIds = new TreeSet<>();
		band.forEach(one -> memberIds.addAll(bandMembers.getOrDefault(one.id(), List.of())));
		Map<EventId, EventProfile> memberProfiles = this.events.profiles(ids(memberIds), 1);
		Map<Long, List<Long>> described = new LinkedHashMap<>();
		Set<Long> describedEvents = new TreeSet<>();
		for (Scored one : band) {
			Map<Long, float[]> centroids = new TreeMap<>();
			for (long member : bandMembers.getOrDefault(one.id(), List.of())) {
				EventProfile memberProfile = memberProfiles.get(new EventId(member));
				if (memberProfile != null && memberProfile.modelVersion().equals(profile.modelVersion())) {
					centroids.put(member, memberProfile.centroid());
				}
			}
			List<Long> chosen = LinkingAsk.describe(subject, profile.id().value(), centroids);
			described.put(one.id(), chosen);
			describedEvents.addAll(chosen);
		}
		Map<EventId, ArticleId> representatives = this.events.representativesOfAll(ids(describedEvents));
		List<LinkingOutcome.AskedFlow> flows = new ArrayList<>();
		for (Scored one : band) {
			List<ArticleId> articles = described.get(one.id())
				.stream()
				.map(event -> representatives.get(new EventId(event)))
				.filter(found -> found != null)
				.distinct()
				.toList();
			flows.add(new LinkingOutcome.AskedFlow(one.id(), articles,
					Math.max(1, bandMembers.getOrDefault(one.id(), List.of()).size())));
		}
		return Optional.of(new LinkingOutcome.Ask(LinkingOutcome.Target.LINK, profile.id().value(), article.get(),
				profile.articleCount(), flows));
	}

	/** 짝 띠의 물을 것 — 후보마다 그 이웃 사건의 대표 기사 하나 */
	private Optional<LinkingOutcome.Ask> askPairs(EventProfile profile, List<Scored> band, List<EventNeighbor> near) {
		Optional<ArticleId> article = representative(profile.id());
		if (article.isEmpty()) {
			return Optional.empty();
		}
		Map<Long, Integer> sizes = new TreeMap<>();
		near.forEach(neighbour -> sizes.put(neighbour.id().value(), neighbour.articleCount()));
		Map<EventId, ArticleId> representatives = this.events
			.representativesOfAll(band.stream().map(one -> new EventId(one.id())).toList());
		List<LinkingOutcome.AskedFlow> flows = new ArrayList<>();
		for (Scored one : band) {
			ArticleId found = representatives.get(new EventId(one.id()));
			flows.add(new LinkingOutcome.AskedFlow(one.id(), (found == null) ? List.of() : List.of(found),
					Math.max(1, sizes.getOrDefault(one.id(), 1))));
		}
		return Optional.of(new LinkingOutcome.Ask(LinkingOutcome.Target.PAIR, profile.id().value(), article.get(),
				profile.articleCount(), flows));
	}

	private Optional<ArticleId> representative(EventId event) {
		return Optional.ofNullable(this.events.representativesOfAll(List.of(event)).get(event));
	}

	/**
	 * 소속이 는 사건마다 아래 기준 이상이고 소속 없는 이웃을 사건 ID 오름차순으로 다시 넣는다(storyline-R12). 점수는 소속이 는 사건이
	 * 판정할 쪽이다 — 이웃 · 말의 무게를 이미 읽은 사건은 그것을 쓰고, 짝으로 연 상대 사건은 자기 구간 · 말로 새로 읽는다
	 */
	private void requeue(Map<Long, EventProfile> gained, Map<Long, List<EventNeighbor>> neighbours,
			Map<Long, TermWeights> weights, LinkingCriteria criteria, Instant now) {
		if (gained.isEmpty()) {
			return;
		}
		Map<Long, Instant> toRequeue = new TreeMap<>();
		for (Map.Entry<Long, EventProfile> entry : gained.entrySet()) {
			Subject subject = Subject.of(entry.getValue());
			List<EventNeighbor> near = neighbours.get(entry.getKey());
			if (near == null) {
				near = this.events.neighbors(entry.getValue().id(),
						LinkingScoring.windowFrom(subject.firstArticleAt(), criteria.window()),
						LinkingScoring.windowTo(subject.lastArticleAt(), criteria.window()),
						LinkingScoring.cosineFloor(criteria.lower()), LinkingScoring.KEY_TERMS,
						LinkingScoring.NEIGHBORS);
			}
			TermWeights own = weights.get(entry.getKey());
			if (own == null) {
				own = weights(subject, criteria);
			}
			List<Scored> scored = score(subject, compared(near), criteria, own);
			List<Long> chosen = LinkingRule.requeue(scored, this.storylines.affiliated(neighbourIds(near)),
					gained.keySet(), criteria);
			for (EventNeighbor neighbour : near) {
				if (chosen.contains(neighbour.id().value())) {
					toRequeue.put(neighbour.id().value(), neighbour.firstArticleAt());
				}
			}
		}
		toRequeue.forEach((event, orderAt) -> this.queue.enqueue(event, orderAt, now));
	}

	/**
	 * 스토리라인들을 ID 순으로 정돈한다(storyline-R13 · R17 · R18) — 판단은 {@link Tidying#decide} 가 한다. 이 정돈에서 이미 지웠거나
	 * 합친 스토리라인은 건너뛰고, 견줄 스토리라인에서도 뺀다. 합치거나 지운 스토리라인이 있으면 끝에 {@link StorylinesRetired} 를 한 번
	 * 발행한다(storyline-R35) — 지운 스토리라인을 가리키던 합쳐진 행은 연쇄로 함께 지워지지만 싣지 않는다(합칠 때 이미 실렸다).
	 * @param represent 남길 때 표현을 다시 정할 스토리라인(소속이 바뀐 것)
	 */
	private void tidy(Collection<Long> storylineIds, Set<Long> represent, Instant now) {
		Set<Long> gone = new HashSet<>();
		List<RetiredStoryline> retired = new ArrayList<>();
		for (long storyline : new TreeSet<>(storylineIds)) {
			if (gone.contains(storyline)) {
				continue;
			}
			List<Long> members = this.storylines.members(List.of(storyline)).getOrDefault(storyline, List.of());
			Map<Long, List<Long>> sharing = Map.of();
			if (!members.isEmpty()) {
				List<Long> others = new ArrayList<>(this.storylines.storylinesOf(members.get(0)));
				others.remove(storyline);
				others.removeAll(gone);
				sharing = this.storylines.members(others);
			}
			switch (Tidying.decide(storyline, members, sharing)) {
				case Tidying.Keep keep -> {
					if (represent.contains(storyline)) {
						represent(storyline, members, now);
					}
					this.relationQueue.enqueue(storyline, now);
				}
				case Tidying.Remove remove -> {
					this.storylines.delete(storyline);
					gone.add(storyline);
					retired.add(new RetiredStoryline(new StorylineId(storyline), null));
				}
				case Tidying.Dissolve dissolve -> {
					// 풀기 — 지우고 남은 사건을 다시 판정한다. 순서 키는 지금 상태로(storyline-R1)
					this.storylines.delete(storyline);
					gone.add(storyline);
					EventId event = new EventId(dissolve.eventId());
					retired.add(new RetiredStoryline(new StorylineId(storyline), event));
					this.queue.enqueue(dissolve.eventId(), PendingEvent.orderAt(event, this.events.find(event)), now);
				}
				case Tidying.MergeInto merge -> {
					for (long merged : merge.merged()) {
						followMerge(merged, merge.survivor(), now);
						this.storylines.merge(merged, merge.survivor(), now);
						gone.add(merged);
						retired.add(new RetiredStoryline(new StorylineId(merged), null));
					}
					if (merge.survivor() == storyline && represent.contains(storyline)) {
						represent(storyline, members, now);
					}
					this.relationQueue.enqueue(merge.survivor(), now);
				}
			}
		}
		if (!retired.isEmpty()) {
			this.publisher.publishEvent(new StorylinesRetired(retired));
		}
	}

	/**
	 * 합쳐질 스토리라인의 관계를 남는 스토리라인으로 옮기고, 그 물은 기록(관계 · 잇기)과 관계 대기 행을 지운다(storyline-R32 ·
	 * storyline-R36) — 합쳐진 행은 남으므로 FK 연쇄가 돌지 않는다. 합치기 전에 부른다
	 */
	private void followMerge(long merged, long survivor, Instant now) {
		this.relations.applyMove(RelationMove.plan(this.relations.all(), merged, survivor), now);
		this.relations.forgetAsked(merged);
		this.relationQueue.delete(merged);
		this.asked.forgetStoryline(merged);
	}

	/** 구성 사건의 지금 표현으로 스토리라인 표현을 다시 정한다 — 읽을 구성 사건이 없으면 그대로 둔다(storyline-R7) */
	private void represent(long storyline, List<Long> members, Instant now) {
		List<EventProfile> readable = readable(members, this.events.profiles(ids(members), LinkingScoring.KEY_TERMS));
		if (!readable.isEmpty()) {
			this.storylines.save(storyline, Representation.of(readable), now);
		}
	}

	/** 판정할 사건의 말의 무게 — 그 사건의 구간(storyline-R9)과 겹치는 사건으로 센 수에서(storyline-R8) */
	private TermWeights weights(Subject subject, LinkingCriteria criteria) {
		EventTermCounts counts = this.events.termCounts(subject.keyTerms(),
				LinkingScoring.windowFrom(subject.firstArticleAt(), criteria.window()),
				LinkingScoring.windowTo(subject.lastArticleAt(), criteria.window()));
		return TermWeights.of(counts.events(), counts.eventsByTerm());
	}

	private static List<Scored> score(Subject subject, List<Compared> compared, LinkingCriteria criteria,
			TermWeights weights) {
		List<Scored> scored = new ArrayList<>();
		for (Compared other : compared) {
			LinkingScoring.score(subject, other, criteria.window(), weights)
				.ifPresent(score -> scored.add(new Scored(other.id(), score)));
		}
		return scored;
	}

	private static List<Compared> compared(List<EventNeighbor> neighbours) {
		return neighbours.stream()
			.map(neighbour -> new Compared(neighbour.id().value(), neighbour.cosine(), neighbour.firstArticleAt(),
					neighbour.lastArticleAt(),
					neighbour.keyTerms().stream().limit(LinkingScoring.KEY_TERMS).map(EventKeyTerm::term).toList()))
			.toList();
	}

	private static List<EventProfile> readable(List<Long> members, Map<EventId, EventProfile> profiles) {
		return members.stream().map(member -> profiles.get(new EventId(member))).filter(p -> p != null).toList();
	}

	private static List<Long> neighbourIds(List<EventNeighbor> neighbours) {
		return neighbours.stream().map(neighbour -> neighbour.id().value()).toList();
	}

	private static List<EventId> ids(Collection<Long> values) {
		return values.stream().map(EventId::new).toList();
	}

}
