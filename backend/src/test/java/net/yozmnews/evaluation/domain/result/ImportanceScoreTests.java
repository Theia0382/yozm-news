package net.yozmnews.evaluation.domain.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 중요도 대표값 — 확률 가중 기대값. 확률은 이진 표현이 정확한 값만 써 정확히 비교한다.
 * <p>
 * 지키는 규칙: evaluation-R15
 */
class ImportanceScoreTests {

	@Test
	void isTheProbabilityWeightedMeanOfTheLevels() {
		assertThat(ImportanceScore.expected(Map.of(1, 0.5, 3, 0.5))).isEqualTo(2.0);
	}

	@Test
	void isDefinedForADistributionThatDoesNotSumToOne() {
		// 합 0.5 — (2 × 0.25 + 4 × 0.25) ÷ 0.5 = 3
		assertThat(ImportanceScore.expected(Map.of(2, 0.25, 4, 0.25))).isEqualTo(3.0);
	}

	@Test
	void isMissingWhenTheProbabilitiesSumToZero() {
		assertThat(ImportanceScore.expected(Map.of(1, 0.0, 5, 0.0))).isNull();
		assertThat(ImportanceScore.expected(Map.of())).isNull();
	}

}
