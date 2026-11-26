package net.yozmnews.evaluation.domain.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;

import net.yozmnews.evaluation.RelationAnswer;
import org.junit.jupiter.api.Test;

/**
 * 관계 판정의 포트 결과 — 답마다의 확률과 스토리라인마다 한 사안 확률은 0 ~ 1 의 유한값이고, 받지 않은 값은 문구에 그 칸 이름을 싣는다.
 */
class RelationTests {

	@Test
	void acceptsTheEndsOfTheProbabilityRange() {
		Relation relation = relation(Map.of(RelationAnswer.NONE, 0.0, RelationAnswer.FIRST_LED_TO_SECOND, 1.0), 0.0, 1.0);

		assertThat(relation.probabilities()).containsEntry(RelationAnswer.NONE, 0.0)
			.containsEntry(RelationAnswer.FIRST_LED_TO_SECOND, 1.0);
		assertThat(relation.firstOneMatter()).isEqualTo(0.0);
		assertThat(relation.secondOneMatter()).isEqualTo(1.0);
	}

	@Test
	void refusesAProbabilityOutsideTheRangeNamingItsField() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> relation(Map.of(RelationAnswer.NONE, 1.2), 0.5, 0.5))
			.withMessage("확률은 0 ~ 1 의 유한값이다 — Relation.probabilities: 1.2");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> relation(Map.of(RelationAnswer.NONE, 0.5), Double.NaN, 0.5))
			.withMessage("확률은 0 ~ 1 의 유한값이다 — Relation.firstOneMatter: NaN");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> relation(Map.of(RelationAnswer.NONE, 0.5), 0.5, Double.POSITIVE_INFINITY))
			.withMessage("확률은 0 ~ 1 의 유한값이다 — Relation.secondOneMatter: Infinity");
	}

	private static Relation relation(Map<RelationAnswer, Double> probabilities, double firstOneMatter,
			double secondOneMatter) {
		return new Relation(probabilities, firstOneMatter, secondOneMatter, "jev", "jev-1.14.0", 0, "{}");
	}

}
