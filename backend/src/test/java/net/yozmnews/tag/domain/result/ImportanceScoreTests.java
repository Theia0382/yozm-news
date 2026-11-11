package net.yozmnews.tag.domain.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 중요도 대표값 — 확률 가중 기대값. 확률은 이진 표현이 정확한 값만 써 정확히 비교한다.
 * <p>
 * 지키는 규칙: tag-R15
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
	void staysWithinTheLevelsThatHaveProbabilityDespiteFloatingPointError() {
		// 나눗셈만 하면 5.000000000000001 · 2.9999999999999996 이 되는 분포 — 저장 제약(1 ~ 5)을 넘거나 그 수준 밖으로 나간다
		assertThat(ImportanceScore.expected(Map.of(1, 0.0, 2, 0.0, 3, 0.0, 4, 0.0, 5, 0.9738340099187002)))
			.isEqualTo(5.0);
		assertThat(ImportanceScore.expected(Map.of(1, 0.0, 2, 0.0, 3, 0.9014274576114836, 4, 0.0, 5, 0.0)))
			.isEqualTo(3.0);
	}

	@Test
	void isMissingWhenTheProbabilitiesSumToZero() {
		assertThat(ImportanceScore.expected(Map.of(1, 0.0, 5, 0.0))).isNull();
		assertThat(ImportanceScore.expected(Map.of())).isNull();
	}

}
