package net.yozmnews.storyline.domain.relation;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.evaluation.RelationAnswer;
import net.yozmnews.storyline.RelationOutcome;

/**
 * 관계 판정 — 정본 · docs/specs/storyline.md §4 · storyline-R29.
 * <p>
 * 답 다섯 가운데 확률이 가장 높은 답으로 정한다. 확률이 같으면 {@link RelationAnswer} 의 선언 순서로 앞의 답이다 — 해당 없음이 맨 앞이라
 * 같으면 맺지 않는다. 경계는 포함한다(기준과 같은 값은 기준 이상이다).
 */
public final class RelationRule {

	private RelationRule() {
	}

	/** 판정의 결말 — 물은 기록에 남긴다(storyline-R30) */
	public static RelationOutcome outcomeOf(Decision decision) {
		return switch (decision) {
			case Decision.Link link -> RelationOutcome.LINKED;
			case Decision.None none -> switch (none.reason()) {
				case NOT_RELATED -> RelationOutcome.NOT_RELATED;
				case LOW_PROBABILITY -> RelationOutcome.LOW_PROBABILITY;
				case NOT_ONE_MATTER -> RelationOutcome.NOT_ONE_MATTER;
				case CYCLE -> RelationOutcome.CYCLE;
				case SECOND_PARENT -> RelationOutcome.SECOND_PARENT;
			};
		};
	}

	/**
	 * @param first 요청의 first 스토리라인
	 * @param second 요청의 second 스토리라인
	 * @param probabilities 답 → 확률 — 다섯 답 모두
	 * @param graph 지금 관계 — 구조 제약은 이 쌍의 지금 관계를 뺀 것으로 본다
	 * @throws IllegalArgumentException 답 다섯이 모두 있지 않을 때
	 */
	public static Decision decide(long first, long second, Map<RelationAnswer, Double> probabilities,
			double firstOneMatter, double secondOneMatter, RelationGraph graph, RelationCriteria criteria) {
		Objects.requireNonNull(graph, "graph");
		Objects.requireNonNull(criteria, "criteria");
		if (!probabilities.keySet().equals(EnumSet.allOf(RelationAnswer.class))) {
			throw new IllegalArgumentException("답 다섯의 확률이 모두 있어야 한다: " + probabilities.keySet());
		}
		RelationAnswer top = RelationAnswer.NONE;
		for (RelationAnswer answer : RelationAnswer.values()) {
			if (probabilities.get(answer) > probabilities.get(top)) {
				top = answer;
			}
		}
		double probability = probabilities.get(top);
		Relation relation = switch (top) {
			case NONE -> null;
			case SECOND_PART_OF_FIRST -> new Relation(first, second, RelationKind.CONTAINS);
			case FIRST_PART_OF_SECOND -> new Relation(second, first, RelationKind.CONTAINS);
			case FIRST_LED_TO_SECOND -> new Relation(first, second, RelationKind.FOLLOWS);
			case SECOND_LED_TO_FIRST -> new Relation(second, first, RelationKind.FOLLOWS);
		};
		if (relation == null) {
			return new Decision.None(Decision.Reason.NOT_RELATED);
		}
		if (probability < criteria.probability()) {
			return new Decision.None(Decision.Reason.LOW_PROBABILITY);
		}
		if (Math.min(firstOneMatter, secondOneMatter) < criteria.oneMatter()) {
			return new Decision.None(Decision.Reason.NOT_ONE_MATTER);
		}
		RelationGraph others = graph.without(first, second);
		if (others.closesCycle(relation)) {
			return new Decision.None(Decision.Reason.CYCLE);
		}
		if (relation.kind() == RelationKind.CONTAINS && others.parentOf(relation.targetId()).isPresent()) {
			return new Decision.None(Decision.Reason.SECOND_PARENT);
		}
		return new Decision.Link(relation, probability);
	}

	/** 판정 */
	public sealed interface Decision {

		/** 맺는다 — 그 쌍의 관계를 이것으로 둔다 */
		record Link(Relation relation, double probability) implements Decision {

			public Link {
				Objects.requireNonNull(relation, "relation");
			}

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
