package net.yozmnews.user.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * 표시 이름 — 앞뒤 공백을 지운 1 ~ 50 코드 포인트, 표시 이름을 받지 않는 계정은 이메일의 {@code @} 앞부분.
 * <p>
 * 지키는 규칙: user-R28
 */
class DisplayNameTests {

	@Test
	void acceptsOneToFiftyCodePointsAfterStripping() {
		assertThat(DisplayName.parse("  김요즘  ")).contains(new DisplayName("김요즘"));
		assertThat(DisplayName.parse("가".repeat(50))).isPresent();
		assertThat(DisplayName.parse("가".repeat(51))).isEmpty();
		assertThat(DisplayName.parse("")).isEmpty();
		assertThat(DisplayName.parse(" \t ")).isEmpty();
	}

	@Test
	void countsCodePointsNotChars() {
		// U+1F600 은 char 둘, 코드 포인트 하나다
		assertThat(DisplayName.parse("😀".repeat(50))).isPresent();
		assertThat(DisplayName.parse("😀".repeat(51))).isEmpty();
	}

	@Test
	void constructorRefusesUnstrippedOrEmptyValues() {
		assertThatIllegalArgumentException().isThrownBy(() -> new DisplayName(" 김요즘"));
		assertThatIllegalArgumentException().isThrownBy(() -> new DisplayName(""));
	}

	@Test
	void emailLocalPartBecomesTheNameCutAtFiftyCodePoints() {
		assertThat(DisplayName.ofEmail(EmailAddress.parse("Admin.Ops@Example.org").orElseThrow()))
			.isEqualTo(new DisplayName("admin.ops"));
		assertThat(DisplayName.ofEmail(EmailAddress.parse("a".repeat(60) + "@example.org").orElseThrow()))
			.isEqualTo(new DisplayName("a".repeat(50)));
	}

}
