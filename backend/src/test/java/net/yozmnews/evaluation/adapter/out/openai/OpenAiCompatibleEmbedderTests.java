package net.yozmnews.evaluation.adapter.out.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.List;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import net.yozmnews.evaluation.EmbedderFailureKind;
import net.yozmnews.evaluation.Embedding;
import net.yozmnews.evaluation.EmbeddingFailure;
import net.yozmnews.evaluation.EmbeddingResult;
import net.yozmnews.evaluation.Embeddings;
import net.yozmnews.evaluation.domain.openai.EmbeddingApiKey;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * OpenAI 호환 외부 임베딩 — WireMock 을 제공자 자리에 두고 연결(경로 · 키 헤더 · 묶음 나누기 · 요청 본문)과 연결 쪽 실패(거절 · 마감 · 본문
 * 상한), 실패 문구의 비밀 값 노출을 본다. 응답 읽기 · 상태 갈래의 판단은 {@code domain/openai} 시험이 본다.
 * <p>
 * 지키는 규칙: ADR-0090 · ADR-0092 · docs/security.md(키 · 응답 본문 · 입력 텍스트가 문구에 남지 않는다)
 */
class OpenAiCompatibleEmbedderTests {

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	private static final String KEY = "sk-test-secret-0123";

	private static final String MARK = "응답표지";

	@Test
	void postsToTheEmbeddingsPathWithTheKeyAndPrefixAndReturnsTheVectors() {
		server.stubFor(post(urlEqualTo("/v1/embeddings")).willReturn(ok(2)));

		EmbeddingResult result = embedder(EmbeddingApiKey.of(KEY), false, 32).embed(List.of("가", "나"));

		assertThat(result).isEqualTo(new Embeddings(List.of(unit(), unit())));
		server.verify(postRequestedFor(urlEqualTo("/v1/embeddings"))
			.withHeader("Authorization", equalTo("Bearer " + KEY))
			.withRequestBody(equalToJson("{\"model\":\"m-1\",\"input\":[\"query: 가\",\"query: 나\"]}")));
	}

	@Test
	void sendsNoAuthorizationWithoutAKeyAndDimensionsWhenAsked() {
		server.stubFor(post(urlEqualTo("/v1/embeddings")).willReturn(ok(1)));

		embedder(null, true, 32).embed(List.of("가"));

		server.verify(postRequestedFor(urlEqualTo("/v1/embeddings")).withHeader("Authorization", absent())
			.withRequestBody(equalToJson("{\"model\":\"m-1\",\"input\":[\"query: 가\"],\"dimensions\":2}")));
	}

	@Test
	void splitsTextsIntoBatchesAndStopsAtTheFirstFailure() {
		server.stubFor(post(urlEqualTo("/v1/embeddings")).inScenario("batches")
			.whenScenarioStateIs(Scenario.STARTED)
			.willReturn(ok(2))
			.willSetStateTo("second"));
		server.stubFor(post(urlEqualTo("/v1/embeddings")).inScenario("batches")
			.whenScenarioStateIs("second")
			.willReturn(ok(2))
			.willSetStateTo("third"));
		server.stubFor(post(urlEqualTo("/v1/embeddings")).inScenario("batches")
			.whenScenarioStateIs("third")
			.willReturn(ok(1)));

		EmbeddingResult result = embedder(null, false, 2).embed(List.of("1", "2", "3", "4", "5"));

		assertThat(result).isInstanceOfSatisfying(Embeddings.class,
				embeddings -> assertThat(embeddings.vectors()).hasSize(5));
		assertThat(server.getAllServeEvents()).hasSize(3);
		server.verify(postRequestedFor(urlEqualTo("/v1/embeddings"))
			.withRequestBody(equalToJson("{\"model\":\"m-1\",\"input\":[\"query: 1\",\"query: 2\"]}")));
		server.verify(postRequestedFor(urlEqualTo("/v1/embeddings"))
			.withRequestBody(equalToJson("{\"model\":\"m-1\",\"input\":[\"query: 3\",\"query: 4\"]}")));
		server.verify(postRequestedFor(urlEqualTo("/v1/embeddings"))
			.withRequestBody(equalToJson("{\"model\":\"m-1\",\"input\":[\"query: 5\"]}")));

		server.resetAll();
		server.stubFor(post(urlEqualTo("/v1/embeddings")).inScenario("fails")
			.whenScenarioStateIs(Scenario.STARTED)
			.willReturn(ok(2))
			.willSetStateTo("second"));
		server.stubFor(post(urlEqualTo("/v1/embeddings")).inScenario("fails")
			.whenScenarioStateIs("second")
			.willReturn(aResponse().withStatus(429).withBody(MARK)));

		EmbeddingResult failed = embedder(null, false, 2).embed(List.of("1", "2", "3", "4", "5"));

		assertThat(failed).isEqualTo(new EmbeddingFailure(EmbedderFailureKind.TRANSIENT, "외부 임베딩 요청이 실패했다 — 상태 429"));
		assertThat(server.getAllServeEvents()).hasSize(2);
	}

	@Test
	void classifiesStatusesAndKeepsSecretsBodiesAndTextsOutOfTheMessage() {
		int[][] cases = { { 400, 0 }, { 401, 1 }, { 503, 2 } };
		EmbedderFailureKind[] kinds = { EmbedderFailureKind.INPUT_REJECTED, EmbedderFailureKind.EMBEDDER_UNAVAILABLE,
				EmbedderFailureKind.TRANSIENT };
		for (int[] c : cases) {
			server.resetAll();
			server.stubFor(post(urlEqualTo("/v1/embeddings"))
				.willReturn(aResponse().withStatus(c[0]).withBody("{\"error\":\"" + MARK + " " + KEY + "\"}")));

			EmbeddingResult result = embedder(EmbeddingApiKey.of(KEY), false, 32).embed(List.of("입력비밀"));

			assertThat(result).isInstanceOfSatisfying(EmbeddingFailure.class, failure -> {
				assertThat(failure.kind()).isEqualTo(kinds[c[1]]);
				assertThat(failure.message()).doesNotContain(KEY, MARK, "입력비밀");
			});
		}
	}

	@Test
	void aRefusedConnectionIsTransient() throws IOException {
		int port;
		try (ServerSocket socket = new ServerSocket(0)) {
			port = socket.getLocalPort();
		}
		OpenAiCompatibleEmbedder embedder = new OpenAiCompatibleEmbedder(URI.create("http://127.0.0.1:" + port + "/v1"),
				"m-1", null, 2, false, "", 32);

		EmbeddingResult result = embedder.embed(List.of("가"));

		assertThat(result).isInstanceOfSatisfying(EmbeddingFailure.class, failure -> {
			assertThat(failure.kind()).isEqualTo(EmbedderFailureKind.TRANSIENT);
			assertThat(failure.message()).startsWith("외부 임베딩 API 와 주고받지 못했다 — ");
		});
	}

	@Test
	void aSlowResponseIsTransient() {
		server.stubFor(post(urlEqualTo("/v1/embeddings")).willReturn(ok(1).withFixedDelay(2_000)));
		OpenAiCompatibleEmbedder embedder = new OpenAiCompatibleEmbedder(base(), "m-1", null, 2, false, "", 32,
				Duration.ofMillis(300), OpenAiCompatibleEmbedder.MAX_RESPONSE_BYTES);

		EmbeddingResult result = embedder.embed(List.of("가"));

		assertThat(result).isInstanceOfSatisfying(EmbeddingFailure.class, failure -> {
			assertThat(failure.kind()).isEqualTo(EmbedderFailureKind.TRANSIENT);
			assertThat(failure.message()).startsWith("외부 임베딩 응답 마감(");
		});
	}

	@Test
	void aBodyOverTheCapMakesTheEmbedderUnavailable() {
		server.stubFor(post(urlEqualTo("/v1/embeddings")).willReturn(ok(1)));
		OpenAiCompatibleEmbedder embedder = new OpenAiCompatibleEmbedder(base(), "m-1", null, 2, false, "", 32,
				OpenAiCompatibleEmbedder.REQUEST_DEADLINE, 16);

		EmbeddingResult result = embedder.embed(List.of("가"));

		assertThat(result).isInstanceOfSatisfying(EmbeddingFailure.class, failure -> {
			assertThat(failure.kind()).isEqualTo(EmbedderFailureKind.EMBEDDER_UNAVAILABLE);
			assertThat(failure.message()).isEqualTo("외부 임베딩 응답 본문이 상한(16 바이트)을 넘었다");
		});
	}

	@Test
	void announcesItsNameModelVersionAndDimensions() {
		OpenAiCompatibleEmbedder embedder = new OpenAiCompatibleEmbedder(URI.create("https://api.example.test/v1"),
				"m-1", null, 256, false, "query: ", 32);

		assertThat(embedder.info().name()).isEqualTo("openai-compatible");
		assertThat(embedder.info().modelVersion()).isEqualTo("openai-compatible:m-1:256:35788b08ad2a");
		assertThat(embedder.info().dimensions()).isEqualTo(256);
	}

	private static OpenAiCompatibleEmbedder embedder(@Nullable EmbeddingApiKey key, boolean sendDimensions, int batchSize) {
		return new OpenAiCompatibleEmbedder(base(), "m-1", key, 2, sendDimensions, "query: ", batchSize);
	}

	private static URI base() {
		return URI.create(server.baseUrl() + "/v1");
	}

	/** n 개의 벡터 [3, 4] — 정규화하면 [0.6, 0.8] */
	private static ResponseDefinitionBuilder ok(int n) {
		StringBuilder data = new StringBuilder();
		for (int i = n - 1; i >= 0; i--) {
			if (data.length() > 0) {
				data.append(',');
			}
			data.append("{\"object\":\"embedding\",\"index\":").append(i).append(",\"embedding\":[3,4]}");
		}
		return aResponse().withStatus(200)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"object\":\"list\",\"data\":[" + data + "],\"model\":\"m-1\",\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}");
	}

	private static Embedding unit() {
		return Embedding.of(new float[] { 0.6f, 0.8f });
	}

}
