package net.yozmnews.event.domain.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.event.domain.feature.EmbedderFailureKind;
import net.yozmnews.event.domain.feature.Embedding;
import net.yozmnews.event.domain.feature.EmbeddingFailure;
import net.yozmnews.event.domain.feature.EmbeddingResult;
import net.yozmnews.event.domain.feature.Embeddings;
import org.junit.jupiter.api.Test;

/**
 * OpenAI 호환 임베딩 응답 읽기 — index 정렬 · 정규화 · 모양이 어긋난 응답의 갈래와 문구(본문 조각 없음)를 본다. 정상 응답은 공식 문서 ·
 * 실측 응답의 모양(object · data[].index · embedding · usage)이다.
 * <p>
 * 지키는 규칙: ADR-0090(응답 규칙) · ADR-0092(갈래)
 */
class OpenAiEmbeddingResponseTests {

	private static final String MARK = "본문표지";

	@Test
	void sortsByIndexAndNormalizes() {
		String body = """
				{"object":"list","model":"m-1","usage":{"prompt_tokens":4,"total_tokens":4},
				 "data":[{"object":"embedding","index":1,"embedding":[0,2]},
				         {"object":"embedding","index":0,"embedding":[3,4]}]}
				""";

		assertThat(OpenAiEmbeddingResponse.read(body, 2)).isEqualTo(
				new Embeddings(List.of(Embedding.of(new float[] { 0.6f, 0.8f }), Embedding.of(new float[] { 0f, 1f }))));
	}

	@Test
	void unreadableBodiesAreUnavailableWithoutBodyFragments() {
		assertUnavailable("{\"" + MARK + "\":", 1);
		assertUnavailable("[\"" + MARK + "\"]", 1);
		assertUnavailable("{\"data\":\"" + MARK + "\"}", 1);
		assertUnavailable("{\"data\":[{\"index\":0,\"embedding\":[1]}]}", 2);
		assertUnavailable("{\"data\":[{\"embedding\":[1]}]}", 1);
		assertUnavailable("{\"data\":[{\"index\":0,\"embedding\":[1]},{\"index\":0,\"embedding\":[1]}]}", 2);
		assertUnavailable("{\"data\":[{\"index\":3,\"embedding\":[1]}]}", 1);
		assertUnavailable("{\"data\":[{\"index\":0,\"embedding\":\"" + MARK + "\"}]}", 1);
		assertUnavailable("{\"data\":[{\"index\":0,\"embedding\":[1,\"" + MARK + "\"]}]}", 1);
		assertUnavailable("{\"data\":[{\"index\":0,\"embedding\":[0,0]}]}", 1);
		assertUnavailable("{\"data\":[{\"index\":0,\"embedding\":[]}]}", 1);
		assertUnavailable("{\"data\":[{\"index\":0,\"embedding\":[1e400]}]}", 1);
	}

	private static void assertUnavailable(String body, int expected) {
		EmbeddingResult result = OpenAiEmbeddingResponse.read(body, expected);

		assertThat(result).isInstanceOfSatisfying(EmbeddingFailure.class, failure -> {
			assertThat(failure.kind()).isEqualTo(EmbedderFailureKind.EMBEDDER_UNAVAILABLE);
			assertThat(failure.message()).startsWith("외부 임베딩 응답을 읽을 수 없다 — ").doesNotContain(MARK);
		});
	}

}
