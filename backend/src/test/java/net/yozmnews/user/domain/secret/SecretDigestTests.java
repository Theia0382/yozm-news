package net.yozmnews.user.domain.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HexFormat;

import org.junit.jupiter.api.Test;

/**
 * 높은 엔트로피 비밀의 다이제스트 — SHA-256, 상수 시간 대조, 저장 값의 길이 확인.
 * <p>
 * 지키는 규칙: user-R2 · user-R30
 */
class SecretDigestTests {

	@Test
	void digestIsTheSha256OfTheUtf8Bytes() {
		// SHA-256("abc") — FIPS 180-2 의 시험 벡터
		assertThat(HexFormat.of().formatHex(SecretDigest.of("abc").value()))
			.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void sameSecretMatchesAndAStoredCopyReadsBack() {
		SecretDigest digest = SecretDigest.of("secret");

		assertThat(digest.matches(SecretDigest.of("secret"))).isTrue();
		assertThat(digest.matches(SecretDigest.of("Secret"))).isFalse();
		assertThat(SecretDigest.stored(digest.value()).matches(digest)).isTrue();
	}

	@Test
	void storedValueMustBeThirtyTwoBytes() {
		assertThatIllegalArgumentException().isThrownBy(() -> SecretDigest.stored(new byte[31]));
	}

	@Test
	void textFormShowsOnlyTheFirstFourBytes() {
		assertThat(SecretDigest.of("abc").toString()).isEqualTo("SecretDigest[ba7816bf…]");
	}

}
