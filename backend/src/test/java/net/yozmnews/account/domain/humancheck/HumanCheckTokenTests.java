package net.yozmnews.account.domain.humancheck;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 사람 확인 토큰 — 1 ~ 2,048자만 받고, 문자열 표현에 값이 없다.
 * <p>
 * 지키는 규칙: account-R33
 */
class HumanCheckTokenTests {

	@Test
	void acceptsUpTo2048Characters() {
		assertThat(HumanCheckToken.parse("x".repeat(2048))).isPresent();
		assertThat(HumanCheckToken.parse("x")).isPresent();
	}

	@Test
	void rejectsLongerEmptyAndMissingTokens() {
		assertThat(HumanCheckToken.parse("x".repeat(2049))).isEmpty();
		assertThat(HumanCheckToken.parse("")).isEmpty();
		assertThat(HumanCheckToken.parse(null)).isEmpty();
	}

	@Test
	void textHidesTheValue() {
		assertThat(HumanCheckToken.parse("0.secret-token-value").orElseThrow().toString())
			.doesNotContain("secret-token-value");
	}

}
