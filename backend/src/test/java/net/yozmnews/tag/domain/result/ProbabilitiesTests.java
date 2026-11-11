package net.yozmnews.tag.domain.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * 확률과 기준의 비교 — 경계는 포함하고, 기준의 눈금(0.100)과 확률의 십진 표현(0.1)을 같은 값으로 본다.
 * <p>
 * 지키는 규칙: tag-R15
 */
class ProbabilitiesTests {

	@Test
	void includesTheBoundaryAcrossScales() {
		assertThat(Probabilities.atLeast(0.1, new BigDecimal("0.100"))).isTrue();
		assertThat(Probabilities.atLeast(0.5, new BigDecimal("0.5"))).isTrue();
	}

	@Test
	void excludesJustBelowTheThreshold() {
		assertThat(Probabilities.atLeast(0.0999, new BigDecimal("0.1"))).isFalse();
		assertThat(Probabilities.atLeast(0.499, new BigDecimal("0.500"))).isFalse();
	}

}
