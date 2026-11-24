package net.yozmnews.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 입력 토큰 어림 — docs/specs/llm.md §3 {@code TokenEstimate}
 */
class TokenEstimateTests {

	@Test
	void estimatesOneTokenPerCodePoint() {
		assertThat(TokenEstimate.tokens(0)).isZero();
		assertThat(TokenEstimate.tokens(1)).isEqualTo(1);
		assertThat(TokenEstimate.tokens(1_501)).isEqualTo(1_501);
		assertThat(TokenEstimate.tokens(120_000)).isEqualTo(120_000);
	}

	@Test
	void refusesNegativeCodePoints() {
		assertThatThrownBy(() -> TokenEstimate.tokens(-1)).isInstanceOf(IllegalArgumentException.class);
	}

}
