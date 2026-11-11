package net.yozmnews.evaluation.domain.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * OpenAI 호환 임베딩 요청 본문 — 차원을 넘길 때만 dimensions, encoding_format 없음을 본다.
 * <p>
 * 지키는 규칙: ADR-0090(요청 규칙)
 */
class OpenAiEmbeddingRequestTests {

	@Test
	void writesModelAndInputsWithoutDimensionsOrEncodingFormat() {
		assertThat(OpenAiEmbeddingRequest.body("m-1", List.of("query: 가", "query: 나"), null))
			.isEqualTo("{\"model\":\"m-1\",\"input\":[\"query: 가\",\"query: 나\"]}");
	}

	@Test
	void writesDimensionsWhenGiven() {
		assertThat(OpenAiEmbeddingRequest.body("m-1", List.of("가"), 256))
			.isEqualTo("{\"model\":\"m-1\",\"input\":[\"가\"],\"dimensions\":256}");
	}

}
