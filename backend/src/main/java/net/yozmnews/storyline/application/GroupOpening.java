package net.yozmnews.storyline.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.FlowCandidate;
import net.yozmnews.evaluation.FlowMembership;
import net.yozmnews.evaluation.FlowMembershipJudgement;
import net.yozmnews.evaluation.FlowMembershipQuery;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventProfile;
import net.yozmnews.storyline.domain.linking.Asked;
import net.yozmnews.storyline.domain.linking.LinkingCriteria;
import net.yozmnews.storyline.domain.linking.LinkingScoring;
import net.yozmnews.storyline.domain.linking.OpeningSelection;
import net.yozmnews.storyline.domain.linking.Representation;
import net.yozmnews.storyline.domain.linking.Score;
import net.yozmnews.storyline.domain.linking.Scored;
import net.yozmnews.storyline.domain.linking.Subject;
import net.yozmnews.storyline.domain.storyline.StorylineProfile;
import net.yozmnews.storyline.domain.storyline.StorylineRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 사안의 조각 위에 상위를 열 때의 평가 — docs/specs/storyline.md §4.
 * <p>
 * 두 조각의 직접 소속 사건마다, 그 사건을 뺀 두 조각의 표현과 견줘 잇기 점수를 매기고 띠 안 사건은 흐름 소속 판정에 묻는다. 판단은
 * {@link OpeningSelection} 이 하고 기준은 잇기 기준이다. 읽기는 짧은 트랜잭션, 묻기는 트랜잭션 밖이다 — 아무것도 쓰지 않는다. 열지는
 * 부르는 쪽({@link RelationRound})이 모듈 잠금 안에서 상태를 다시 확인한 뒤 정한다. 판정 원본과 물은 기록이 가리킬 상위의 ID 는 여기서
 * 미리 받는다 — 열지 않으면 그 ID 는 쓰이지 않는다. 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class GroupOpening {

	private final EventDirectory events;

	private final StorylineRepository storylines;

	private final FlowMembership membership;

	private final TransactionTemplate transaction;

	GroupOpening(EventDirectory events, StorylineRepository storylines, FlowMembership membership,
			TransactionTemplate transaction) {
		this.events = events;
		this.storylines = storylines;
		this.membership = membership;
		this.transaction = transaction;
	}

	/**
	 * 두 조각의 사건을 평가한다 — 트랜잭션 밖에서 부른다.
	 * @param first 한 조각
	 * @param second 다른 조각
	 */
	Evaluated evaluate(long first, long second, LinkingCriteria criteria) {
		Read read = Objects.requireNonNull(this.transaction.execute(status -> read(first, second, criteria)), "read");
		Map<Long, Asked> asked = new LinkedHashMap<>();
		for (Scored one : OpeningSelection.toAsk(read.scored(), criteria)) {
			EventProfile profile = read.profiles().get(new EventId(one.id()));
			switch (ask(read, profile)) {
				case Answer.Probability answer -> asked.put(one.id(), answer.asked());
				case Answer.Rejected rejected -> asked.put(one.id(), Asked.UNANSWERED);
				case Answer.Nothing nothing -> {
					// 물을 수 없었다 — 기록 없이 들지 않는다
				}
				case Answer.Transient failed -> {
					return new Evaluated.Transient();
				}
				case Answer.Unavailable failed -> {
					return new Evaluated.Unavailable(failed.message());
				}
			}
		}
		List<Evaluated.Member> members = new ArrayList<>();
		for (OpeningSelection.Passed passed : OpeningSelection.passed(read.scored(), asked, criteria)) {
			members.add(new Evaluated.Member(passed.scored().id(), passed.scored().score(), passed.asked()));
		}
		Map<Long, Evaluated.AskedEvent> records = new LinkedHashMap<>();
		asked.forEach((event, answer) -> records.put(event, new Evaluated.AskedEvent(answer,
				read.profiles().get(new EventId(event)).articleCount())));
		return new Evaluated.Passed(read.reservedId(), members, records);
	}

	/** 두 조각의 사건과 그 점수 — 모델 버전이 조각과 같은 사건만 */
	private Read read(long first, long second, LinkingCriteria criteria) {
		Optional<StorylineProfile> profile = this.storylines.profile(first, 1);
		Map<Long, List<Long>> members = this.storylines.members(List.of(first, second));
		TreeSet<Long> union = new TreeSet<>();
		members.values().forEach(union::addAll);
		Map<EventId, EventProfile> profiles = new LinkedHashMap<>();
		if (profile.isPresent()) {
			this.events.profiles(union.stream().map(EventId::new).toList(), LinkingScoring.KEY_TERMS)
				.forEach((id, found) -> {
					if (found.modelVersion().equals(profile.get().modelVersion())) {
						profiles.put(id, found);
					}
				});
		}
		List<EventProfile> readable = LinkingMaterials.readable(List.copyOf(union), profiles);
		long reservedId = this.storylines.reserveId();
		List<Scored> scored = new ArrayList<>();
		for (EventProfile event : readable) {
			Subject subject = Subject.of(event);
			// 그 사건을 뺀 표현과 견준다 — 이미 든 스토리라인을 볼 때와 같다(storyline-R9)
			Representation.without(readable, event.id())
				.map(rest -> rest.comparedTo(event.id().value(), subject))
				.ifPresent(compared -> scored.addAll(LinkingMaterials.score(subject, List.of(compared), criteria,
						LinkingMaterials.weights(this.events, subject, criteria))));
		}
		return new Read(reservedId, union, profiles, scored);
	}

	/** 띠 사건 하나를 묻는다 — 판정할 기사는 그 사건의 대표 기사, 후보는 두 조각의 사건 가운데 가까운 것으로 서술한 상위 하나 */
	private Answer ask(Read read, EventProfile profile) {
		Subject subject = Subject.of(profile);
		List<EventId> described = LinkingMaterials.describe(profile, subject, read.union(), read.profiles())
			.stream()
			.map(EventId::new)
			.toList();
		List<EventId> needed = new ArrayList<>(described);
		needed.add(profile.id());
		Map<EventId, ArticleId> representatives = this.events.representativesOfAll(needed);
		ArticleId article = representatives.get(profile.id());
		List<ArticleId> articles = described.stream()
			.map(representatives::get)
			.filter(Objects::nonNull)
			.distinct()
			.toList();
		if (article == null || articles.isEmpty()) {
			return new Answer.Nothing();
		}
		FlowMembershipJudgement judgement = this.membership
			.judge(new FlowMembershipQuery(article, List.of(new FlowCandidate(read.reservedId(), articles))));
		return switch (judgement) {
			case FlowMembershipJudgement.Judged judged -> {
				Double probability = judged.probabilities().get(read.reservedId());
				yield (probability == null || judged.judgement() == null) ? new Answer.Nothing()
						: new Answer.Probability(new Asked(probability, judged.judgement().value()));
			}
			case FlowMembershipJudgement.ArticleMissing missing -> new Answer.Nothing();
			case FlowMembershipJudgement.Failed failed -> switch (failed.kind()) {
				case TRANSIENT -> new Answer.Transient();
				case ARTICLE_REJECTED -> new Answer.Rejected();
				case EVALUATOR_UNAVAILABLE -> new Answer.Unavailable(failed.message());
			};
		};
	}

	/** 평가의 결과 */
	sealed interface Evaluated {

		/**
		 * 평가를 마쳤다.
		 *
		 * @param reservedId 미리 받은 상위의 ID — 판정 원본이 이 ID 를 흐름으로 가리킨다
		 * @param members 상위에 들 사건 — 사건 ID 순
		 * @param asked 띠 사건에 물은 결과 — 사건 ID → 결과. 들지 못한 사건과 입력이 거부된 사건(확률 없음)도 있다
		 */
		record Passed(long reservedId, List<Member> members, Map<Long, AskedEvent> asked) implements Evaluated {

			public Passed {
				members = List.copyOf(members);
				asked = Map.copyOf(asked);
			}

		}

		/** 평가기의 일시 실패 — 그 스토리라인을 다음 회차로 미룬다. 받은 답은 버린다 */
		record Transient() implements Evaluated {
		}

		/** 평가기를 쓸 수 없다 — 회차를 멈춘다 */
		record Unavailable(String message) implements Evaluated {
		}

		/**
		 * 상위에 들 사건 하나.
		 *
		 * @param asked 띠 안에서 확률로 들었으면 그 결과 — 위 기준 이상으로 들었으면 null
		 */
		record Member(long eventId, Score score, @Nullable Asked asked) {
		}

		/**
		 * 물은 띠 사건 하나의 결과.
		 *
		 * @param eventArticles 물었을 때 그 사건의 기사 수 — 물은 기록에 남는다
		 */
		record AskedEvent(Asked asked, int eventArticles) {
		}

	}

	/** 읽은 것 */
	private record Read(long reservedId, TreeSet<Long> union, Map<EventId, EventProfile> profiles, List<Scored> scored) {
	}

	/** 띠 사건 하나를 물은 답 */
	private sealed interface Answer {

		record Probability(Asked asked) implements Answer {
		}

		/** 평가기가 그 기사를 거부했다 — 확률 없이 기록한다 */
		record Rejected() implements Answer {
		}

		/** 대표 기사나 서술이 없어 묻지 못했거나, 답에 확률이 없다 */
		record Nothing() implements Answer {
		}

		record Transient() implements Answer {
		}

		record Unavailable(String message) implements Answer {
		}

	}

}
