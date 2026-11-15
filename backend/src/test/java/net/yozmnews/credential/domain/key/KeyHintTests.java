package net.yozmnews.credential.domain.key;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.platform.SecretValue;
import org.junit.jupiter.api.Test;

/**
 * 힌트 규칙 — 20자 이상의 키는 끝 4자, 그보다 짧은 키는 빈 문자열. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: credential-R9
 */
class KeyHintTests {

	@Test
	void takesTheLastFourCharactersOfAKeyOfTwentyOrMore() {
		assertThat(KeyHint.MIN_LENGTH).isEqualTo(20);
		assertThat(KeyHint.of(SecretValue.of("sk-test-0123456789abcdWXYZ"))).isEqualTo("WXYZ");
		// 꼭 20자
		assertThat(KeyHint.of(SecretValue.of("0123456789abcdefghij"))).isEqualTo("ghij");
	}

	@Test
	void givesNoHintForAShorterKey() {
		// 19자
		assertThat(KeyHint.of(SecretValue.of("0123456789abcdefghi"))).isEmpty();
		assertThat(KeyHint.of(SecretValue.of("yozm-local-valid"))).isEmpty();
	}

}
