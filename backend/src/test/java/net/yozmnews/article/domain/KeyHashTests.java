package net.yozmnews.article.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HexFormat;

import org.junit.jupiter.api.Test;

/**
 * SHA-256 다이제스트 — 기대값은 {@code sha256sum} 으로 따로 계산한 16진수다
 */
class KeyHashTests {

	private static final String DIGEST = "4bf04c4dc63907accf6c03a1cbda25cbceecf0f0de3be79491cdfb8bc1497521";

	@Test
	void digestsUtf8Bytes() {
		assertThat(KeyHash.of("https://example.com/a/B?id=7")).hasToString(DIGEST);
	}

	@Test
	void comparesByContent() {
		KeyHash stored = KeyHash.fromBytes(HexFormat.of().parseHex(DIGEST));

		assertThat(stored).isEqualTo(KeyHash.of("https://example.com/a/B?id=7"));
		assertThat(stored).hasSameHashCodeAs(KeyHash.of("https://example.com/a/B?id=7"));
		assertThat(stored).isNotEqualTo(KeyHash.of("https://example.com/a/B?id=8"));
	}

	@Test
	void copiesBytes() {
		byte[] bytes = HexFormat.of().parseHex(DIGEST);
		KeyHash hash = KeyHash.fromBytes(bytes);
		bytes[0] = 0;
		hash.bytes()[1] = 0;

		assertThat(hash).hasToString(DIGEST);
	}

	@Test
	void rejectsAWrongLength() {
		assertThatIllegalArgumentException().isThrownBy(() -> KeyHash.fromBytes(new byte[31]));
	}

}
