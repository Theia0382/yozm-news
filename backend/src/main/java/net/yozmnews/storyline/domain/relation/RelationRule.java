package net.yozmnews.storyline.domain.relation;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.evaluation.RelationAnswer;
import net.yozmnews.storyline.RelationOutcome;

/**
 * 관계 판정 — 정본 · docs/specs/storyline.md §4 · storyline-R29.
 * <p>
 * 답 가운데 확률이 가장 높은 답으로 정한다. 확률이 같으면 {@link RelationAnswer} 의 선언 순서로 앞의 답이다 — 해당 없음이 맨 앞이라
 * 같으면 맺지 않는다. 경계는 포함한다(기준과 같은 값은 기준 이상이다). 가장 높은 답이 같은 사안의 조각이면 관계가 아니라
 * {@link Decision.Fragments} 다 — 두 스토리라인은 어느 쪽도 다른 쪽의 하위가 아니다.
 */
public final class RelationRule {

	private RelationRule() {
	}

	/**
	 * 맺지 않는 판정의 결말 — 물은 기록에 남긴다(storyline-R30). 맺으면 {@link RelationOutcome#LINKED} 이고, 조각이면 묶기의 결과가
	 * 결말을 정한다({@link Grouping#outcomeOf}).
	 */
	public static RelationOutcome outcomeOf(Decision.None none) {
		return switch (none.reason()) {
			case NOT_RELATED -> RelationOutcome.NOT_RELATED;
			case LOW_PROBABILITY -> RelationOutcome.LOW_PROBABILITY;
			case NOT_ONE_MATTER -> RelationOutcome.NOT_ONE_MATTER;
			case CYCLE -> RelationOutcome.CYCLE;
			case SECOND_PARENT -> RelationOutcome.SECOND_PARENT;
		};
	}

	/**
	 * @param first 요청의 first 스토리라인
	 * @param second 요청의 second 스토리라인
	 * @param probabilities 답 → 확률 — {@link RelationAnswer} 의 답 모두
	 * @param graph 지금 관계 — 구조 제약은 이 쌍의 지금 관계를 뺀 것으로 본다
	 * @throws IllegalArgumentException 답이 모두 있지 않을 때
	 */
	public static Decision decide(long first, long second, Map<RelationAnswer, Double> probabilities,
			double firstOneMatter, double secondOneMatter, RelationGraph graph, RelationCriteria criteria) {
		Objects.requireNonNull(graph, "graph");
		Objects.requireNonNull(criteria, "criteria");
		if (!probabilities.keySet().equals(EnumSet.allOf(RelationAnswer.class))) {
			throw new IllegalArgumentException("모든 답의 확률이 있어야 한다: " + probabilities.keySet());
		}
		RelationAnswer highest = RelationAnswer.NONE;
		for (RelationAnswer answer : RelationAnswer.values()) {
			if (probabilities.get(answer) > probabilities.get(highest)) {
				highest = answer;
			}
		}
		RelationAnswer top = highest;
		double probability = probabilities.get(top);
		Optional<Decision> gated = gated(probability, firstOneMatter, secondOneMatter, criteria);
		RelationGraph others = graph.without(first, second);
		return switch (top) {
			case NONE -> new Decision.None(Decision.Reason.NOT_RELATED);
			case SAME_MATTER -> gated.orElseGet(() -> new Decision.Fragments(first, second, probability));
			case SECOND_PART_OF_FIRST -> gated
				.orElseGet(() -> linked(new Relation(first, second, RelationKind.CONTAINS), probability, others));
			case FIRST_PART_OF_SECOND -> gated
				.orElseGet(() -> linked(new Relation(second, first, RelationKind.CONTAINS), probability, others));
			case FIRST_LED_TO_SECOND -> gated
				.orElseGet(() -> linked(new Relation(first, second, RelationKind.FOLLOWS), probability, others));
			case SECOND_LED_TO_FIRST -> gated
				.orElseGet(() -> linked(new Relation(second, first, RelationKind.FOLLOWS), probability, others));
		};
	}

	/** 구조 제약을 지나면 맺는다 — 순환을 먼저 본다 */
	private static Decision linked(Relation relation, double probability, RelationGraph others) {
		if (others.closesCycle(relation)) {
			return new Decision.None(Decision.Reason.CYCLE);
		}
		if (relation.kind() == RelationKind.CONTAINS && others.parentOf(relation.targetId()).isPresent()) {
			return new Decision.None(Decision.Reason.SECOND_PARENT);
		}
		return new Decision.Link(relation, probability);
	}

	/** 확률 기준 · 한 사안 기준에 걸리면 그 까닭으로 맺지 않는다 — 관계와 조각이 같은 순서로 거친다 */
	private static Optional<Decision> gated(double probability, double firstOneMatter, double secondOneMatter,
			RelationCriteria criteria) {
		if (probability < criteria.probability()) {
			return Optional.of(new Decision.None(Decision.Reason.LOW_PROBABILITY));
		}
		if (Math.min(firstOneMatter, secondOneMatter) < criteria.oneMatter()) {
			return Optional.of(new Decision.None(Decision.Reason.NOT_ONE_MATTER));
		}
		return Optional.empty();
	}

	/** 판정 */
	public sealed interface Decision {

		/** 맺는다 — 그 쌍의 관계를 이것으로 둔다 */
		record Link(Relation relation, double probability) implements Decision {

			public Link {
				Objects.requireNonNull(relation, "relation");
			}

		}

		/**
		 * 같은 사안의 조각이다 — 두 스토리라인 사이에 관계를 맺지 않고, 그 쌍의 지금 관계를 지운다. 구조(순환 · 상위)는 보지 않았다 —
		 * 두 스토리라인을 한 상위 아래 어떻게 묶는지는 {@link Grouping} 이 지금 관계로 정한다.
		 */
		record Fragments(long first, long second, double probability) implements Decision {
		}

		/** 맺지 않는다 — 그 쌍의 지금 관계를 지운다 */
		record None(Reason reason) implements Decision {

			public None {
				Objects.requireNonNull(reason, "reason");
			}

		}

		/** 맺지 않는 까닭 */
		enum Reason {

			/** 가장 높은 답이 해당 없음이다 */
			NOT_RELATED,

			/** 가장 높은 답의 확률이 확률 기준 미만이다 */
			LOW_PROBABILITY,

			/** 두 스토리라인의 "한 사안" 확률 가운데 작은 쪽이 한 사안 기준 미만이다 */
			NOT_ONE_MATTER,

			/** 넣으면 순환이 된다 */
			CYCLE,

			/** 포함의 하위가 될 스토리라인에 이미 다른 상위가 있다 */
			SECOND_PARENT

		}

	}

}
