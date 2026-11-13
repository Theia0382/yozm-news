package net.yozmnews.user.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * 이메일 정규화 — 앞뒤 공백 · 소문자 · 길이 · {@code @} 하나.
 * <p>
 * 지키는 규칙: user-R13 · user-R19
 */
class EmailAddressTests {

	@Test
	void trimsAndLowercases() {
		assertThat(EmailAddress.parse(" Alice@Example.COM\t")).contains(new EmailAddress("alice@example.com"));
	}

	@Test
	void lowercasesWithoutTheDefaultLocale() {
		Locale saved = Locale.getDefault();
		Locale.setDefault(Locale.forLanguageTag("tr"));
		try {
			// 터키어 로캘이면 I 가 점 없는 ı 로 바뀐다 — 정규화는 로캘을 쓰지 않는다
			assertThat(EmailAddress.parse("TITLE@EXAMPLE.IO")).contains(new EmailAddress("title@example.io"));
		}
		finally {
			Locale.setDefault(saved);
		}
	}

	@Test
	void acceptsUpTo254Characters() {
		String local = "a".repeat(64);
		String domain = "b".repeat(254 - 64 - 1);

		assertThat(EmailAddress.parse(local + "@" + domain)).map(EmailAddress::value).hasValue(local + "@" + domain);
		assertThat(EmailAddress.parse(local + "@" + domain + "c")).isEmpty();
	}

	@Test
	void rejectsMissingOrRepeatedAtAndEmptySides() {
		assertThat(EmailAddress.parse("alice.example.com")).isEmpty();
		assertThat(EmailAddress.parse("a@b@example.com")).isEmpty();
		assertThat(EmailAddress.parse("@example.com")).isEmpty();
		assertThat(EmailAddress.parse("alice@")).isEmpty();
		assertThat(EmailAddress.parse("   ")).isEmpty();
	}

	@Test
	void normalizesUnreadableInputTheSameWay() {
		assertThat(EmailAddress.normalize("  Not An Email  ")).isEqualTo("not an email");
	}

	@Test
	void constructorRefusesUnnormalizedValues() {
		assertThatIllegalArgumentException().isThrownBy(() -> new EmailAddress("Alice@example.com"));
		assertThatIllegalArgumentException().isThrownBy(() -> new EmailAddress(" alice@example.com"));
		assertThatIllegalArgumentException().isThrownBy(() -> new EmailAddress("alice.example.com"));
	}

}
