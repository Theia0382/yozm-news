package net.yozmnews.user.domain.secret;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 초대 토큰 — 128비트 base64url 22자, 앞뒤 공백만 너그럽게 읽는다.
 * <p>
 * 지키는 규칙: user-R31
 */
class InvitationTokenTests {

	@Test
	void generatedTokensAreTwentyTwoUrlSafeCharacters() {
		SecureRandom random = new SecureRandom();
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 100; i++) {
			String token = InvitationToken.generate(random).value();
			assertThat(token).matches("^[A-Za-z0-9_-]{22}$");
			seen.add(token);
		}
		assertThat(seen).hasSize(100);
	}

	@Test
	void parsingStripsSpacesAndRefusesOtherShapes() {
		InvitationToken token = InvitationToken.parse(" AAAAAAAAAAAAAAAAAAAAAA ").orElseThrow();

		assertThat(token.value()).isEqualTo("AAAAAAAAAAAAAAAAAAAAAA");
		assertThat(token.digest().matches(SecretDigest.of("AAAAAAAAAAAAAAAAAAAAAA"))).isTrue();
		assertThat(InvitationToken.parse("AAAAAAAAAAAAAAAAAAAAA")).as("21자").isEmpty();
		assertThat(InvitationToken.parse("AAAAAAAAAAAAAAAAAAAAAAA")).as("23자").isEmpty();
		assertThat(InvitationToken.parse("AAAAAAAAAAAAAAAAAAAAA+")).as("base64url 밖").isEmpty();
		assertThat(InvitationToken.parse("")).isEmpty();
	}

	@Test
	void textFormHidesTheToken() {
		assertThat(InvitationToken.parse("AAAAAAAAAAAAAAAAAAAAAA").orElseThrow().toString()).doesNotContain("AAAA");
	}

}
