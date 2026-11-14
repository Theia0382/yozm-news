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

	@Test
	void saysWhichRuleTheKeyBroke() {
		assertThatIllegalArgumentException().isThrownBy(() -> EmbeddingApiKey.of(""))
			.withMessage("임베딩 API 키는 1자 이상 512자 이하여야 한다 (지금 0자)");
		assertThatIllegalArgumentException().isThrownBy(() -> EmbeddingApiKey.of("k".repeat(513)))
			.withMessage("임베딩 API 키는 1자 이상 512자 이하여야 한다 (지금 513자)");
		assertThatIllegalArgumentException().isThrownBy(() -> EmbeddingApiKey.of("sk with space"))
			.withMessage("임베딩 API 키에는 공백 · 제어 문자 · ASCII 밖의 문자를 쓸 수 없다");
		assertThatIllegalArgumentException().isThrownBy(() -> EmbeddingApiKey.of("sk-키"))
			.withMessage("임베딩 API 키에는 공백 · 제어 문자 · ASCII 밖의 문자를 쓸 수 없다");
	}

}
