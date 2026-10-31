package net.yozmnews.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 운영자 자격의 형식 · 열림 · 대조.
 * <p>
 * 지키는 규칙: 운영자 접근 통제(docs/specs/platform.md §5)
 */
class AdminCredentialTests {

	private static final String TOKEN_32 = "0123456789abcdef0123456789abcdef";

	@Test
	void missingOrEmptyValueClosesTheAdminFunctions() {
		assertThat(AdminCredential.of(null).open()).isFalse();
		assertThat(AdminCredential.of("").open()).isFalse();
		assertThat(AdminCredential.closed().open()).isFalse();
	}

	@Test
	void acceptsTheLengthBoundaries() {
		assertThat(AdminCredential.of(TOKEN_32).open()).isTrue();
		assertThat(AdminCredential.of("a".repeat(512)).open()).isTrue();
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
		assertThat(AdminCredential.of("AZaz09-._~+/AZaz09-._~+/AZaz09==").open()).isTrue();
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
	void closedCredentialMatchesNothing() {
		assertThat(AdminCredential.closed().matches(TOKEN_32)).isFalse();
		assertThat(AdminCredential.closed().matches("")).isFalse();
	}

	@Test
	void textDoesNotCarryTheValue() {
		assertThat(AdminCredential.of(TOKEN_32)).hasToString("AdminCredential[open]");
		assertThat(AdminCredential.closed()).hasToString("AdminCredential[closed]");
	}

}
