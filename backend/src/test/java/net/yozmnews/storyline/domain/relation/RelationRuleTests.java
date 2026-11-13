package net.yozmnews.storyline.domain.relation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.evaluation.RelationAnswer;
import net.yozmnews.storyline.domain.relation.RelationRule.Decision;
import org.junit.jupiter.api.Test;

/**
 * 관계 판정 — 답마다 종류 · 방향, 확률 · 한 사안 기준의 경계, 같은 확률, 순환, 둘째 상위, 다시 묻는 쌍.
 * <p>
 * 지키는 규칙: storyline-R25 · storyline-R29
 */
class RelationRuleTests {

	private static final RelationCriteria CRITERIA = new RelationCriteria(0.60, 0.20, 3, 0.88, Duration.ofDays(14));

	private static final RelationGraph EMPTY = new RelationGraph(List.of());

	@Test
	void eachAnswerBecomesItsKindAndDirection() {
		assertThat(decide(RelationAnswer.SECOND_PART_OF_FIRST, 0.7, EMPTY))
			.isEqualTo(new Decision.Link(new Relation(1, 2, RelationKind.CONTAINS), 0.7));
		assertThat(decide(RelationAnswer.FIRST_PART_OF_SECOND, 0.7, EMPTY))
			.isEqualTo(new Decision.Link(new Relation(2, 1, RelationKind.CONTAINS), 0.7));
		assertThat(decide(RelationAnswer.FIRST_LED_TO_SECOND, 0.7, EMPTY))
			.isEqualTo(new Decision.Link(new Relation(1, 2, RelationKind.FOLLOWS), 0.7));
		assertThat(decide(RelationAnswer.SECOND_LED_TO_FIRST, 0.7, EMPTY))
			.isEqualTo(new Decision.Link(new Relation(2, 1, RelationKind.FOLLOWS), 0.7));
		assertThat(decide(RelationAnswer.NONE, 0.7, EMPTY)).isEqualTo(new Decision.None(Decision.Reason.NOT_RELATED));
	}

	@Test
	void theProbabilityThresholdIncludesItsBoundary() {
		assertThat(decide(RelationAnswer.SECOND_PART_OF_FIRST, 0.60, EMPTY)).isInstanceOf(Decision.Link.class);
		assertThat(decide(RelationAnswer.SECOND_PART_OF_FIRST, 0.59, EMPTY))
			.isEqualTo(new Decision.None(Decision.Reason.LOW_PROBABILITY));
	}

	@Test
	void theSmallerOneMatterProbabilityMustReachItsThreshold() {
		Map<RelationAnswer, Double> probabilities = probabilities(RelationAnswer.FIRST_LED_TO_SECOND, 0.7);

		assertThat(RelationRule.decide(1, 2, probabilities, 0.20, 0.9, EMPTY, CRITERIA)).isInstanceOf(Decision.Link.class);
		assertThat(RelationRule.decide(1, 2, probabilities, 0.9, 0.19, EMPTY, CRITERIA))
			.isEqualTo(new Decision.None(Decision.Reason.NOT_ONE_MATTER));
		assertThat(RelationRule.decide(1, 2, probabilities, 0.19, 0.9, EMPTY, CRITERIA))
			.isEqualTo(new Decision.None(Decision.Reason.NOT_ONE_MATTER));
	}

	@Test
	void aTieWithNoneDoesNotLinkAndATieBetweenRelationsTakesTheEarlierAnswer() {
		Map<RelationAnswer, Double> withNone = new EnumMap<>(RelationAnswer.class);
		withNone.put(RelationAnswer.NONE, 0.45);
		withNone.put(RelationAnswer.SECOND_PART_OF_FIRST, 0.45);
		withNone.put(RelationAnswer.FIRST_PART_OF_SECOND, 0.04);
		withNone.put(RelationAnswer.FIRST_LED_TO_SECOND, 0.03);
		withNone.put(RelationAnswer.SECOND_LED_TO_FIRST, 0.03);
		Map<RelationAnswer, Double> between = new EnumMap<>(RelationAnswer.class);
		between.put(RelationAnswer.NONE, 0.0);
		between.put(RelationAnswer.SECOND_PART_OF_FIRST, 0.1);
		between.put(RelationAnswer.FIRST_PART_OF_SECOND, 0.1);
		between.put(RelationAnswer.FIRST_LED_TO_SECOND, 0.4);
		between.put(RelationAnswer.SECOND_LED_TO_FIRST, 0.4);
		RelationCriteria low = new RelationCriteria(0.40, 0.20, 3, 0.88, Duration.ofDays(14));

		assertThat(RelationRule.decide(1, 2, withNone, 0.9, 0.9, EMPTY, low))
			.isEqualTo(new Decision.None(Decision.Reason.NOT_RELATED));
		assertThat(RelationRule.decide(1, 2, between, 0.9, 0.9, EMPTY, low))
			.isEqualTo(new Decision.Link(new Relation(1, 2, RelationKind.FOLLOWS), 0.4));
	}

	@Test
	void aRelationThatWouldCloseACycleIsNotLinked() {
		// 쌍 (1, 3) 을 묻는다 — 3 ⊃ 2 ⊃ 1 인데 1 ⊃ 3(세 걸음). 같은 쌍의 두 걸음은 다시 묻는 쌍이라 순환으로 세지 않는다
		RelationGraph chain = new RelationGraph(
				List.of(new Relation(3, 2, RelationKind.CONTAINS), new Relation(2, 1, RelationKind.CONTAINS)));

		assertThat(RelationRule.decide(1, 3, probabilities(RelationAnswer.SECOND_PART_OF_FIRST, 0.9), 0.9, 0.9, chain,
				CRITERIA))
			.isEqualTo(new Decision.None(Decision.Reason.CYCLE));
		// 이어짐도 이어짐끼리 순환을 이루지 않는다 — 1 → 2 → 3 인데 3 → 1
		RelationGraph follows = new RelationGraph(
				List.of(new Relation(1, 2, RelationKind.FOLLOWS), new Relation(2, 3, RelationKind.FOLLOWS)));
		assertThat(RelationRule.decide(1, 3, probabilities(RelationAnswer.SECOND_LED_TO_FIRST, 0.9), 0.9, 0.9, follows,
				CRITERIA))
			.isEqualTo(new Decision.None(Decision.Reason.CYCLE));
		// 종류가 다르면 순환이 아니다 — 포함 3 ⊃ 2 ⊃ 1 위에 이어짐 1 → 3
		assertThat(RelationRule.decide(1, 3, probabilities(RelationAnswer.FIRST_LED_TO_SECOND, 0.9), 0.9, 0.9, chain,
				CRITERIA))
			.isEqualTo(new Decision.Link(new Relation(1, 3, RelationKind.FOLLOWS), 0.9));
	}

	@Test
	void aChildKeepsItsOneParent() {
		RelationGraph parented = new RelationGraph(List.of(new Relation(9, 2, RelationKind.CONTAINS)));

		assertThat(decide(RelationAnswer.SECOND_PART_OF_FIRST, 0.9, parented))
			.isEqualTo(new Decision.None(Decision.Reason.SECOND_PARENT));
		// 상위가 여럿의 상위인 것은 된다 — 9 ⊃ 2 에 9 ⊃ ... 가 아니라 2 가 다른 것의 상위가 되는 것
		assertThat(decide(RelationAnswer.FIRST_PART_OF_SECOND, 0.9, parented))
			.isEqualTo(new Decision.Link(new Relation(2, 1, RelationKind.CONTAINS), 0.9));
		// 이어짐은 앞이 여럿이어도 된다
		RelationGraph followed = new RelationGraph(List.of(new Relation(9, 2, RelationKind.FOLLOWS)));
		assertThat(decide(RelationAnswer.FIRST_LED_TO_SECOND, 0.9, followed))
			.isEqualTo(new Decision.Link(new Relation(1, 2, RelationKind.FOLLOWS), 0.9));
	}

	@Test
	void aPairAskedAgainIsNotBlockedByItsOwnRelation() {
		// 1 ⊃ 2 로 맺은 쌍을 다시 물어 방향이 뒤집혔다 — 자기 관계는 순환 · 둘째 상위로 세지 않는다
		RelationGraph linked = new RelationGraph(List.of(new Relation(1, 2, RelationKind.CONTAINS)));

		assertThat(decide(RelationAnswer.FIRST_PART_OF_SECOND, 0.9, linked))
			.isEqualTo(new Decision.Link(new Relation(2, 1, RelationKind.CONTAINS), 0.9));
		assertThat(decide(RelationAnswer.SECOND_PART_OF_FIRST, 0.9, linked))
			.isEqualTo(new Decision.Link(new Relation(1, 2, RelationKind.CONTAINS), 0.9));
	}

	@Test
	void theOutcomeOfEachDecisionAndTheFiveAnswers() {
		assertThat(RelationOutcome.of(new Decision.Link(new Relation(1, 2, RelationKind.FOLLOWS), 0.9)))
			.isEqualTo(RelationOutcome.LINKED);
		assertThat(RelationOutcome.of(new Decision.None(Decision.Reason.NOT_RELATED))).isEqualTo(RelationOutcome.NOT_RELATED);
		assertThat(RelationOutcome.of(new Decision.None(Decision.Reason.LOW_PROBABILITY)))
			.isEqualTo(RelationOutcome.LOW_PROBABILITY);
		assertThat(RelationOutcome.of(new Decision.None(Decision.Reason.NOT_ONE_MATTER)))
			.isEqualTo(RelationOutcome.NOT_ONE_MATTER);
		assertThat(RelationOutcome.of(new Decision.None(Decision.Reason.CYCLE))).isEqualTo(RelationOutcome.CYCLE);
		assertThat(RelationOutcome.of(new Decision.None(Decision.Reason.SECOND_PARENT)))
			.isEqualTo(RelationOutcome.SECOND_PARENT);
		assertThatIllegalArgumentException()
			.isThrownBy(() -> RelationRule.decide(1, 2, Map.of(RelationAnswer.NONE, 0.9), 0.9, 0.9, EMPTY, CRITERIA))
			.withMessageStartingWith("답 다섯의 확률이 모두 있어야 한다");
	}

	private static Decision decide(RelationAnswer top, double probability, RelationGraph graph) {
		return RelationRule.decide(1, 2, probabilities(top, probability), 0.9, 0.9, graph, CRITERIA);
	}

	/** top 이 probability, 나머지는 0.01 */
	private static Map<RelationAnswer, Double> probabilities(RelationAnswer top, double probability) {
		Map<RelationAnswer, Double> probabilities = new EnumMap<>(RelationAnswer.class);
		for (RelationAnswer answer : RelationAnswer.values()) {
			probabilities.put(answer, (answer == top) ? probability : 0.01);
		}
		return probabilities;
	}

}
