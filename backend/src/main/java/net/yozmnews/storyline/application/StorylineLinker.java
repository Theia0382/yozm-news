package net.yozmnews.storyline.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventKeyTerm;
import net.yozmnews.event.EventNeighbor;
import net.yozmnews.event.EventProfile;
import net.yozmnews.event.EventTermCounts;
import net.yozmnews.event.EventView;
import net.yozmnews.storyline.domain.linking.Compared;
import net.yozmnews.storyline.domain.linking.Cosine;
import net.yozmnews.storyline.domain.linking.LinkingCriteria;
import net.yozmnews.storyline.domain.linking.LinkingRule;
import net.yozmnews.storyline.domain.linking.LinkingScoring;
import net.yozmnews.storyline.domain.linking.Representation;
import net.yozmnews.storyline.domain.linking.Scored;
import net.yozmnews.storyline.domain.linking.Subject;
import net.yozmnews.storyline.domain.linking.TermWeights;
import net.yozmnews.storyline.domain.queue.LinkingQueueRepository;
import net.yozmnews.storyline.domain.queue.PendingEvent;
import net.yozmnews.storyline.domain.storyline.LinkJudgement;
import net.yozmnews.storyline.domain.storyline.LinkedBy;
import net.yozmnews.storyline.domain.storyline.StorylineRepository;
import net.yozmnews.storyline.domain.storyline.StorylineSnapshot;
import net.yozmnews.storyline.domain.storyline.Tidying;
import org.springframework.stereotype.Component;

/**
 * 사건 하나의 따르기 · 판정 · 정돈 · 쓰기 — docs/specs/storyline.md §4 · storyline-R5 ~ storyline-R14 · storyline-R17 ·
 * storyline-R18.
 * <p>
 * 트랜잭션 안에서 부른다. 첫 문장이 모듈 잠금이라 판정은 한 번에 하나다. 사건의 지금 상태({@link EventDirectory#find})로 지워짐 ·
 * 합쳐짐 · 그대로를 가르고, 그대로면 그 사건이 든 스토리라인의 표현을 다시 정하고 그 사건의 말의 무게를 읽은 뒤 붙이기 · 짝 · 다시 넣기를
 * 판정한다. 끝으로 소속이 바뀌었거나 그 사건이 든 스토리라인을 정돈한다. 판단은 {@code domain/linking} ·
 * {@code domain/storyline} 이 하고, 이 클래스는 읽기 · 쓰기를 잇는다.
 */
@Component
class StorylineLinker {

	private final EventDirectory events;

	private final StorylineRepository storylines;

	private final LinkingQueueRepository queue;

	StorylineLinker(EventDirectory events, StorylineRepository storylines, LinkingQueueRepository queue) {
		this.events = events;
		this.storylines = storylines;
		this.queue = queue;
	}

	/**
	 * 사건 하나를 판정한다 — 트랜잭션 안에서 부른다.
	 * @param criteriaId 판정에 쓴 기준의 기준 이력 ID
	 * @param now 소속 · 표현을 쓴 시각
	 */
	void link(long eventId, LinkingCriteria criteria, long criteriaId, Instant now) {
		this.storylines.lockLinking();
		EventId id = new EventId(eventId);
		Optional<EventView> current = this.events.find(id);
		if (current.isEmpty()) {
			// 지워진 사건 — 소속을 지우고 그 스토리라인들을 정돈한다(storyline-R6 · R17)
			List<Long> owned = this.storylines.storylinesOf(eventId);
			for (long storyline : owned) {
				this.storylines.removeMember(storyline, eventId);
			}
			tidy(owned, Set.copyOf(owned), now);
			return;
		}
		long survivor = current.get().id().value();
		if (survivor != eventId) {
			follow(eventId, survivor, now);
			return;
		}
		judge(id, criteria, criteriaId, now);
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

	private void judge(EventId id, LinkingCriteria criteria, long criteriaId, Instant now) {
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
			return;
		}
		Subject subject = Subject.of(profile);
		double threshold = criteria.threshold();
		double floor = LinkingScoring.cosineFloor(threshold);
		Instant from = LinkingScoring.windowFrom(subject.firstArticleAt(), criteria.window());
		Instant to = LinkingScoring.windowTo(subject.lastArticleAt(), criteria.window());

		// 이미 든 스토리라인 — 표현을 지금 값으로 다시 정해 쓰고, 이 사건을 뺀 표현과 견준다(storyline-R7 · R9)
		List<Compared> compared = new ArrayList<>();
		for (long storyline : own) {
			List<EventProfile> readable = readable(members.getOrDefault(storyline, List.of()), profiles);
			if (!readable.isEmpty()) {
				this.storylines.save(storyline, Representation.of(readable), now);
			}
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
		Optional<Scored> attach = LinkingRule.attach(scored, Set.copyOf(own), threshold);
		if (attach.isPresent()) {
			// 붙이기(storyline-R10)
			long storyline = attach.get().id();
			this.storylines.addMember(storyline, eventId,
					LinkJudgement.judged(LinkedBy.RULE, attach.get().score(), criteriaId, now));
			attached.add(storyline);
			gained.put(eventId, profile);
		}
		else if (own.isEmpty()) {
			// 짝(storyline-R11)
			List<EventNeighbor> near = this.events.neighbors(id, from, to, floor, LinkingScoring.KEY_TERMS,
					LinkingScoring.NEIGHBORS);
			neighbours.put(eventId, near);
			List<Scored> scoredNear = score(subject, compared(near), criteria, weights.get(eventId));
			Optional<Scored> pair = LinkingRule.pair(scoredNear, this.storylines.affiliated(neighbourIds(near)),
					threshold);
			if (pair.isPresent()) {
				EventId partnerId = new EventId(pair.get().id());
				EventProfile partner = this.events.profiles(List.of(partnerId), LinkingScoring.KEY_TERMS).get(partnerId);
				if (partner != null) {
					long storyline = this.storylines.open(Representation.of(List.of(profile, partner)), now);
					LinkJudgement judgement = LinkJudgement.judged(LinkedBy.PAIR, pair.get().score(), criteriaId, now);
					this.storylines.addMember(storyline, eventId, judgement);
					this.storylines.addMember(storyline, partnerId.value(), judgement);
					gained.put(eventId, profile);
					gained.put(partnerId.value(), partner);
				}
			}
		}
		requeue(gained, neighbours, weights, criteria, now);
		// 정돈 — 이미 든 스토리라인은 앞에서 표현을 다시 정했으므로 정돈만, 붙인 스토리라인은 표현까지(storyline-R17)
		Set<Long> tidied = new TreeSet<>(own);
		tidied.addAll(attached);
		tidy(tidied, attached, now);
	}

	/**
	 * 소속이 는 사건마다 기준 이상이고 소속 없는 이웃을 사건 ID 오름차순으로 다시 넣는다(storyline-R12). 점수는 소속이 는 사건이 판정할
	 * 쪽이다 — 이웃 · 말의 무게를 이미 읽은 사건은 그것을 쓰고, 짝으로 연 상대 사건은 자기 구간 · 말로 새로 읽는다
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
						LinkingScoring.cosineFloor(criteria.threshold()), LinkingScoring.KEY_TERMS,
						LinkingScoring.NEIGHBORS);
			}
			TermWeights own = weights.get(entry.getKey());
			if (own == null) {
				own = weights(subject, criteria);
			}
			List<Scored> scored = score(subject, compared(near), criteria, own);
			List<Long> chosen = LinkingRule.requeue(scored, this.storylines.affiliated(neighbourIds(near)),
					gained.keySet(), criteria.threshold());
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
	 * 합친 스토리라인은 건너뛰고, 견줄 스토리라인에서도 뺀다.
	 * @param represent 남길 때 표현을 다시 정할 스토리라인(소속이 바뀐 것)
	 */
	private void tidy(Collection<Long> storylineIds, Set<Long> represent, Instant now) {
		Set<Long> gone = new HashSet<>();
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
				}
				case Tidying.Remove remove -> {
					this.storylines.delete(storyline);
					gone.add(storyline);
				}
				case Tidying.Dissolve dissolve -> {
					// 풀기 — 지우고 남은 사건을 다시 판정한다. 순서 키는 지금 상태로(storyline-R1)
					this.storylines.delete(storyline);
					gone.add(storyline);
					EventId event = new EventId(dissolve.eventId());
					this.queue.enqueue(dissolve.eventId(), PendingEvent.orderAt(event, this.events.find(event)), now);
				}
				case Tidying.MergeInto merge -> {
					for (long merged : merge.merged()) {
						this.storylines.merge(merged, merge.survivor(), now);
						gone.add(merged);
					}
					if (merge.survivor() == storyline && represent.contains(storyline)) {
						represent(storyline, members, now);
					}
				}
			}
		}
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
