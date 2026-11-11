package net.yozmnews.evaluation.domain.openai;

import static net.yozmnews.evaluation.EmbedderFailureKind.EMBEDDER_UNAVAILABLE;
import static net.yozmnews.evaluation.EmbedderFailureKind.INPUT_REJECTED;
import static net.yozmnews.evaluation.EmbedderFailureKind.TRANSIENT;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * OpenAI 호환 임베딩 API 의 상태 코드 갈래를 본다.
 * <p>
 * 지키는 규칙: ADR-0092
 */
class OpenAiEmbeddingErrorsTests {

	@Test
	void inputProblemsAreRejections() {
		assertThat(OpenAiEmbeddingErrors.classify(400)).isEqualTo(INPUT_REJECTED);
		assertThat(OpenAiEmbeddingErrors.classify(413)).isEqualTo(INPUT_REJECTED);
		assertThat(OpenAiEmbeddingErrors.classify(422)).isEqualTo(INPUT_REJECTED);
	}

	@Test
	void keyAndModelProblemsMakeTheEmbedderUnavailable() {
		assertThat(OpenAiEmbeddingErrors.classify(401)).isEqualTo(EMBEDDER_UNAVAILABLE);
		assertThat(OpenAiEmbeddingErrors.classify(403)).isEqualTo(EMBEDDER_UNAVAILABLE);
		assertThat(OpenAiEmbeddingErrors.classify(404)).isEqualTo(EMBEDDER_UNAVAILABLE);
	}

	@Test
	void timeoutsLimitsAndServerErrorsAreTransient() {
		assertThat(OpenAiEmbeddingErrors.classify(408)).isEqualTo(TRANSIENT);
		assertThat(OpenAiEmbeddingErrors.classify(409)).isEqualTo(TRANSIENT);
		assertThat(OpenAiEmbeddingErrors.classify(429)).isEqualTo(TRANSIENT);
		assertThat(OpenAiEmbeddingErrors.classify(500)).isEqualTo(TRANSIENT);
		assertThat(OpenAiEmbeddingErrors.classify(503)).isEqualTo(TRANSIENT);
	}

	@Test
	void otherStatusesMakeTheEmbedderUnavailable() {
		assertThat(OpenAiEmbeddingErrors.classify(302)).isEqualTo(EMBEDDER_UNAVAILABLE);
		assertThat(OpenAiEmbeddingErrors.classify(418)).isEqualTo(EMBEDDER_UNAVAILABLE);
	}

}
