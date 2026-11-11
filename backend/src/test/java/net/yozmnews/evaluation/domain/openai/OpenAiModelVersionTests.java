package net.yozmnews.evaluation.domain.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 외부 임베딩의 모델 버전 — 같은 설정이면 같고, 주소 · 입력 앞 문자열 · 차원 · 차원 요청 여부 가운데 하나라도 다르면 다르다.
 * <p>
 * 지키는 규칙: ADR-0090 · evaluation-R45
 */
class OpenAiModelVersionTests {

	private static final URI BASE = URI.create("https://api.example.test/v1");

	@Test
	void isTheModelDimensionsAndAHashOfTheRest() {
		// 앞 12 hex 는 시험 밖에서 printf 'https://api.example.test/v1\nquery: \nfalse' | sha256sum 으로 구한 값이다
		assertThat(OpenAiModelVersion.of("m-1", 256, BASE, "query: ", false))
			.isEqualTo("openai-compatible:m-1:256:35788b08ad2a");
		assertThat(OpenAiModelVersion.of("m-1", 256, URI.create("https://api.example.test/v1/"), "query: ", false))
			.isEqualTo("openai-compatible:m-1:256:35788b08ad2a");
	}

	@Test
	void anyDifferenceMakesAnotherVersion() {
		Set<String> versions = Set.of(OpenAiModelVersion.of("m-1", 256, BASE, "query: ", false),
				OpenAiModelVersion.of("m-1", 256, URI.create("https://other.example.test/v1"), "query: ", false),
				OpenAiModelVersion.of("m-1", 256, BASE, "", false), OpenAiModelVersion.of("m-1", 512, BASE, "query: ", false),
				OpenAiModelVersion.of("m-1", 256, BASE, "query: ", true));

		assertThat(versions).hasSize(5);
	}

}
