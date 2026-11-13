package net.yozmnews.user.domain.secret;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 복구 코드 — 160비트 Crockford Base32 32자, 4자씩 8묶음으로 보이고, 읽을 때 대소문자 · 공백 · {@code -} 와 헷갈리는 글자를 너그럽게 읽는다.
 * <p>
 * 지키는 규칙: user-R30
 */
class RecoveryCodeTests {

	private static final String DISPLAY = "^[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){7}$";

	@Test
	void generatedCodesShowAsEightGroupsOfFour() {
		SecureRandom random = new SecureRandom();
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 100; i++) {
			String shown = RecoveryCode.generate(random).display();
			assertThat(shown).matches(DISPLAY);
			seen.add(shown);
		}
		assertThat(seen).as("무작위 값이 겹치지 않는다").hasSize(100);
	}

	@Test
	void parsingIgnoresCaseSpacesAndHyphensAndReadsLookAlikes() {
		RecoveryCode code = RecoveryCode.parse("0123-4567-89AB-CDEF-GHJK-MNPQ-RSTV-WXYZ").orElseThrow();

		assertThat(code.display()).isEqualTo("0123-4567-89AB-CDEF-GHJK-MNPQ-RSTV-WXYZ");
		assertThat(RecoveryCode.parse("0123 4567 89ab cdef ghjk mnpq rstv wxyz").orElseThrow().digest()
			.matches(code.digest())).isTrue();
		// O → 0, I · L → 1
		assertThat(RecoveryCode.parse("O123-4567-89AB-CDEF-GHJK-MNPQ-RSTV-WXYZ").orElseThrow().digest()
			.matches(code.digest())).isTrue();
		assertThat(RecoveryCode.parse("0I23-4567-89AB-CDEF-GHJK-MNPQ-RSTV-WXYZ").orElseThrow().display())
			.isEqualTo("0123-4567-89AB-CDEF-GHJK-MNPQ-RSTV-WXYZ");
		assertThat(RecoveryCode.parse("0l23-4567-89AB-CDEF-GHJK-MNPQ-RSTV-WXYZ").orElseThrow().display())
			.isEqualTo("0123-4567-89AB-CDEF-GHJK-MNPQ-RSTV-WXYZ");
	}

	@Test
	void wrongLengthOrForeignLettersAreNotACode() {
		assertThat(RecoveryCode.parse("0123-4567-89AB-CDEF-GHJK-MNPQ-RSTV-WXY")).isEmpty();
		assertThat(RecoveryCode.parse("0123-4567-89AB-CDEF-GHJK-MNPQ-RSTV-WXYZ0")).isEmpty();
		// U 는 Crockford Base32 에 없다
		assertThat(RecoveryCode.parse("U123-4567-89AB-CDEF-GHJK-MNPQ-RSTV-WXYZ")).isEmpty();
		assertThat(RecoveryCode.parse("")).isEmpty();
	}

	@Test
	void textFormHidesTheCode() {
		RecoveryCode code = RecoveryCode.parse("0123-4567-89AB-CDEF-GHJK-MNPQ-RSTV-WXYZ").orElseThrow();
		assertThat(code.toString()).doesNotContain("0123").doesNotContain("WXYZ");
	}

}
