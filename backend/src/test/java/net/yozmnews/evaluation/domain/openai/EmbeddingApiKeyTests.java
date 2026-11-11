package net.yozmnews.evaluation.domain.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * 임베딩 API 키의 형식 검사와 값 숨김을 본다.
 * <p>
 * 지키는 규칙: docs/security.md(비밀 값이 문구에 남지 않는다)
 */
class EmbeddingApiKeyTests {

	@Test
	void carriesTheKeyOnlyInTheAuthorizationHeader() {
		EmbeddingApiKey key = EmbeddingApiKey.of("sk-or-v1-0123");

		assertThat(key.authorization()).isEqualTo("Bearer sk-or-v1-0123");
		assertThat(key).hasToString("EmbeddingApiKey[숨김]");
	}

	@Test
	void refusesKeysThatCannotGoIntoAHeaderWithoutQuotingThem() {
		for (String bad : new String[] { "", "sk with space", "sk\tkey", "sk-키", "k".repeat(513) }) {
			assertThatIllegalArgumentException().isThrownBy(() -> EmbeddingApiKey.of(bad))
				.satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(bad.isEmpty() ? "\u0000" : bad));
		}
		assertThat(EmbeddingApiKey.of("k".repeat(512)).authorization()).hasSize("Bearer ".length() + 512);
	}

}
