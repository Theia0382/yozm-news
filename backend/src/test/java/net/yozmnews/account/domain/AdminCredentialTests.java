package net.yozmnews.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 운영자 자격의 형식 · 설정 여부 · 대조.
 * <p>
 * 지키는 규칙: account-R2
 */
class AdminCredentialTests {

	private static final String TOKEN_32 = "0123456789abcdef0123456789abcdef";

	@Test
	void missingOrEmptyValueLeavesTheTokenUnconfigured() {
		assertThat(AdminCredential.of(null).configured()).isFalse();
		assertThat(AdminCredential.of("").configured()).isFalse();
		assertThat(AdminCredential.unconfigured().configured()).isFalse();
	}

	@Test
	void acceptsTheLengthBoundaries() {
		assertThat(AdminCredential.of(TOKEN_32).configured()).isTrue();
		assertThat(AdminCredential.of("a".repeat(512)).configured()).isTrue();
	}

	@Test
	void rejectsALengthOutsideTheBoundariesWithoutQuotingTheValue() {
		String short31 = "S3cr3tValueOfThirtyOneCharsXyzQ";
		assertThat(short31).hasSize(31);
		assertThatIllegalArgumentException().isThrownBy(() -> AdminCredential.of(short31))
			.withMessage("운영자 토큰은 32자 이상 512자 이하여야 한다 (지금 31자)")
			.withMessageNotContaining(short31);
		assertThatIllegalArgumentException().isThrownBy(() -> AdminCredential.of("a".repeat(513)))
			.withMessage("운영자 토큰은 32자 이상 512자 이하여야 한다 (지금 513자)");
	}

	@ParameterizedTest
	@ValueSource(strings = { "0123456789abcdef 0123456789abcdef", "0123456789abcdef0123456789abcdef가",
			"0123456789abcdef:0123456789abcdef", "=0123456789abcdef0123456789abcdef", "0123456789abcdef=0123456789abcdef" })
	void rejectsCharactersOutsideTheBearerTokenSyntaxWithoutQuotingTheValue(String value) {
		assertThatIllegalArgumentException().isThrownBy(() -> AdminCredential.of(value))
			.withMessage("운영자 토큰은 영문 대소문자 · 숫자 · - . _ ~ + / 와 끝의 = 만 쓸 수 있다");
	}

	@Test
	void acceptsEveryBearerTokenCharacterAndTrailingPadding() {
		assertThat(AdminCredential.of("AZaz09-._~+/AZaz09-._~+/AZaz09==").configured()).isTrue();
	}

	@Test
	void matchesOnlyTheSameToken() {
		AdminCredential credential = AdminCredential.of(TOKEN_32);

		assertThat(credential.matches(TOKEN_32)).isTrue();
		assertThat(credential.matches("0123456789abcdef0123456789abcdeF")).isFalse();
		assertThat(credential.matches("0123456789abcdef0123456789abcde")).isFalse();
		assertThat(credential.matches(TOKEN_32 + "0")).isFalse();
		assertThat(credential.matches("")).isFalse();
		assertThat(credential.matches(null)).isFalse();
	}

	@Test
	void unconfiguredCredentialMatchesNothing() {
		assertThat(AdminCredential.unconfigured().matches(TOKEN_32)).isFalse();
		assertThat(AdminCredential.unconfigured().matches("")).isFalse();
	}

	@Test
	void textDoesNotCarryTheValue() {
		assertThat(AdminCredential.of(TOKEN_32)).hasToString("AdminCredential[configured]");
		assertThat(AdminCredential.unconfigured()).hasToString("AdminCredential[unconfigured]");
	}

}
