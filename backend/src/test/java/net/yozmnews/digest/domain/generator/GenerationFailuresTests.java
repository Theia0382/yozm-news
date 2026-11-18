package net.yozmnews.digest.domain.generator;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.digest.domain.digest.DigestFailure;
import net.yozmnews.llm.LlmFailureKind;
import org.junit.jupiter.api.Test;

/**
 * 관문의 실패 종류 → 다이제스트 실패 코드 — 종류마다 하나
 */
class GenerationFailuresTests {

	@Test
	void eachKindHasItsOwnCode() {
		assertThat(GenerationFailures.of(LlmFailureKind.INVALID_KEY)).isEqualTo(DigestFailure.KEY_INVALID);
		assertThat(GenerationFailures.of(LlmFailureKind.PERMISSION_DENIED)).isEqualTo(DigestFailure.PERMISSION_DENIED);
		assertThat(GenerationFailures.of(LlmFailureKind.QUOTA_EXHAUSTED)).isEqualTo(DigestFailure.QUOTA_EXHAUSTED);
		assertThat(GenerationFailures.of(LlmFailureKind.TRANSIENT)).isEqualTo(DigestFailure.PROVIDER_UNAVAILABLE);
		assertThat(GenerationFailures.of(LlmFailureKind.UNEXPECTED)).isEqualTo(DigestFailure.PROVIDER_UNEXPECTED);
	}

}
