package net.yozmnews.user.domain.credential;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.user.PasswordProblem;
import org.junit.jupiter.api.Test;

/**
 * 비밀번호 규칙 — NFKC 정규화 뒤 코드 포인트로 8 ~ 128 자, 문자 종류 · 목록 검사 없음.
 * <p>
 * 지키는 규칙: user-R14
 */
class PasswordPolicyTests {

	@Test
	void acceptsEightToHundredTwentyEightCodePoints() {
		assertThat(PasswordPolicy.check("abcdefg")).contains(PasswordProblem.TOO_SHORT);
		assertThat(PasswordPolicy.check("abcdefgh")).isEmpty();
		assertThat(PasswordPolicy.check("a".repeat(128))).isEmpty();
		assertThat(PasswordPolicy.check("a".repeat(129))).contains(PasswordProblem.TOO_LONG);
	}

	@Test
	void countsCodePointsNotChars() {
		// U+1F600 은 char 둘, 코드 포인트 하나다 — 일곱 개는 7 코드 포인트(14 char)라 짧다
		assertThat(PasswordPolicy.check("😀".repeat(7))).contains(PasswordProblem.TOO_SHORT);
		assertThat(PasswordPolicy.check("😀".repeat(8))).isEmpty();
	}

	@Test
	void normalizesWithNfkcBeforeCounting() {
		// 전각 영문은 반각으로, 합자 ﬁ(U+FB01) 는 f · i 두 글자로 바뀐다
		assertThat(PasswordPolicy.normalize("ＡＢＣ")).isEqualTo("ABC");
		assertThat(PasswordPolicy.normalize("ﬁ")).isEqualTo("fi");
		assertThat(PasswordPolicy.check(PasswordPolicy.normalize("ﬁ".repeat(4)))).isEmpty();
		assertThat(PasswordPolicy.check("ﬁ".repeat(4))).contains(PasswordProblem.TOO_SHORT);
	}

	@Test
	void composesCombiningSequencesBeforeCounting() {
		// e + 결합 예음 부호(U+0301) 는 NFKC 뒤 é(U+00E9) 하나다 — 정규화 전 8 코드 포인트가 정규화 뒤 4 라 짧다
		String fourComposedLetters = "e\u0301".repeat(4);
		assertThat(PasswordPolicy.check(fourComposedLetters)).isEmpty();
		assertThat(PasswordPolicy.normalize(fourComposedLetters)).isEqualTo("\u00e9".repeat(4));
		assertThat(PasswordPolicy.check(PasswordPolicy.normalize(fourComposedLetters))).contains(PasswordProblem.TOO_SHORT);
		assertThat(PasswordPolicy.check(PasswordPolicy.normalize("e\u0301".repeat(8)))).isEmpty();
	}

	@Test
	void imposesNoCharacterClassesOrCommonPasswordList() {
		assertThat(PasswordPolicy.check("password")).isEmpty();
		assertThat(PasswordPolicy.check("        ")).isEmpty();
		assertThat(PasswordPolicy.check("비밀번호여덟글자")).isEmpty();
	}

}
