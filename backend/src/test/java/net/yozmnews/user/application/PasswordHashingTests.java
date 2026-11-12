package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 비밀번호 해시 — Argon2id 파라미터를 해시 문자열에 담고, 더미 대조는 언제나 거짓이다.
 * <p>
 * 지키는 규칙: user-R15 · user-R17
 */
class PasswordHashingTests {

	private static final String PARAMETERS = "$argon2id$v=19$m=19456,t=2,p=1$";

	private final PasswordHashing hashing = new PasswordHashing();

	@Test
	void hashCarriesArgon2idParameters() {
		String hash = this.hashing.hash("correct horse");

		assertThat(hash).startsWith(PARAMETERS);
		assertThat(this.hashing.hash("correct horse")).as("솔트가 매번 다르다").isNotEqualTo(hash);
	}

	@Test
	void matchesOnlyTheSamePassword() {
		String hash = this.hashing.hash("correct horse");

		assertThat(this.hashing.matches("correct horse", hash)).isTrue();
		assertThat(this.hashing.matches("correct horsE", hash)).isFalse();
	}

	@Test
	void nobodyMatchesAnything() {
		assertThat(this.hashing.matchesNobody("correct horse")).isFalse();
		assertThat(this.hashing.matchesNobody("")).isFalse();
	}

	@Test
	void dummyHashUsesTheSameParameters() throws Exception {
		var field = PasswordHashing.class.getDeclaredField("nobody");
		field.setAccessible(true);

		assertThat((String) field.get(this.hashing)).startsWith(PARAMETERS);
	}

}
