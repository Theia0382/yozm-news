package net.yozmnews.llm.adapter.out.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.platform.SecretValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 키 하나의 Anthropic 클라이언트 — WireMock 을 공급자 자리에 두고 키가 실리는 헤더, 환경 변수의 주소 · 키 · 토큰을 읽지 않음, 실패
 * 종류와 문구, 출력에 키 · 응답 본문이 남지 않음, 전송의 방어(리다이렉트 · 본문 상한), 닫기를 본다. 응답은 공급자의 실제 응답 꼴 · 공식
 * 문서의 오류 형식을 따온 대체 응답이다.
 * <p>
 * 지키는 규칙: llm-R6 · llm-R8 · llm-R9 · llm-R11 · llm-R12 · llm-R13 · llm-R14
 */
@ExtendWith(OutputCaptureExtension.class)
class AnthropicProviderClientTests {

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	/** 리다이렉트가 가리키는 다른 호스트 자리 */
	@RegisterExtension
	static WireMockExtension elsewhere = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	private static final String KEY = "sk-ant-wiremock-only-0123456789wxyz";

	private static final String MODELS = "{\"data\":[{\"type\":\"model\",\"id\":\"claude-x\",\"display_name\":\"Claude X\","
			+ "\"created_at\":\"2026-01-01T00:00:00Z\"}],\"has_more\":false,\"first_id\":\"claude-x\","
			+ "\"last_id\":\"claude-x\"}";

	/** 틀린 키에 Anthropic 이 주는 실제 본문 꼴 */
	private static final String BAD_KEY = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\","
			+ "\"message\":\"invalid x-api-key\"},\"request_id\":\"req_011Ch24DMMNRV6roH4JJwBP8\"}";

	private static final String MESSAGE = "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\","
			+ "\"model\":\"claude-x\",\"content\":[{\"type\":\"text\",\"text\":\"pong\"}],\"stop_reason\":\"end_turn\","
			+ "\"stop_sequence\":null,\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}";

	@Test
	void theTestJvmCarriesTheEnvironmentThatMustNotBeUsed() {
		// 아래 시험들의 전제 — 이 값들이 없으면 "환경 변수를 읽지 않는다" · "로그를 꺼 둔다"는 확인이 헛돈다
		assertThat(System.getenv("ANTHROPIC_BASE_URL")).isEqualTo("http://127.0.0.1:9");
		assertThat(System.getenv("ANTHROPIC_API_KEY")).isEqualTo("env-key-must-not-be-used");
		assertThat(System.getenv("ANTHROPIC_AUTH_TOKEN")).isEqualTo("env-token-must-not-be-used");
		assertThat(System.getenv("ANTHROPIC_LOG")).isEqualTo("debug");
	}

	@Test
	void listsModelsWithTheGivenKeyAtTheGivenAddress() {
		server.stubFor(get(urlPathEqualTo("/v1/models")).willReturn(json(200, MODELS)));

		try (AnthropicProviderClient client = client()) {
			assertThat(client.probe()).isEqualTo(new ProbeResult.Reachable());
		}

		assertThat(server.getAllServeEvents()).hasSize(1);
		server.verify(getRequestedFor(urlPathEqualTo("/v1/models")).withHeader("x-api-key", equalTo(KEY))
			.withHeader("anthropic-version", matching("\\d{4}-\\d{2}-\\d{2}"))
			.withHeader("Authorization", absent()));
		// 키는 한 번만 실린다
		assertThat(server.getAllServeEvents().get(0).getRequest().getHeaders().getHeader("x-api-key").values())
			.containsExactly(KEY);
	}

	@Test
	void sortsProviderErrorsAndSaysOnlyTheStatus() {
		assertThat(probe(json(401, BAD_KEY)))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.INVALID_KEY, "Anthropic 키 확인 요청이 실패했다 — 상태 401"));
		assertThat(probe(json(403, error("permission_error", "Your API key does not have permission"))))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.PERMISSION_DENIED, "Anthropic 키 확인 요청이 실패했다 — 상태 403"));
		assertThat(probe(json(402, error("billing_error", "There's an issue with your billing"))))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.QUOTA_EXHAUSTED, "Anthropic 키 확인 요청이 실패했다 — 상태 402"));
		assertThat(probe(json(529, error("overloaded_error", "The API is temporarily overloaded"))))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.TRANSIENT, "Anthropic 키 확인 요청이 실패했다 — 상태 529"));
		assertThat(probe(json(400, error("invalid_request_error", "spend limit reached"))))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.UNEXPECTED, "Anthropic 키 확인 요청이 실패했다 — 상태 400"));
	}

	@Test
	void tellsTheSpendCapFromRateLimitByTheRetryAfterHeader() {
		String body = error("rate_limit_error", "rate limit or spend cap");

		assertThat(probe(json(429, body)))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.QUOTA_EXHAUSTED, "Anthropic 키 확인 요청이 실패했다 — 상태 429"));
		assertThat(probe(json(429, body).withHeader("retry-after", "7")))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.TRANSIENT, "Anthropic 키 확인 요청이 실패했다 — 상태 429"));
	}

	@Test
	void doesNotRetryAFailedRequest() {
		probe(json(529, error("overloaded_error", "The API is temporarily overloaded")));

		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	@Test
	void keepsTheKeyAndTheResponseBodyOutOfMessagesAndOutput(CapturedOutput output) {
		System.out.println("출력표지-anthropic");

		ProbeResult result = probe(json(401, BAD_KEY));

		assertThat(result).isInstanceOfSatisfying(ProbeResult.Failed.class,
				failed -> assertThat(failed.message()).doesNotContain(KEY, "wxyz", "invalid x-api-key"));
		// 대조 — 출력이 캡처된다. 아래의 부정 단언이 빈 출력 때문에 통과하는 것이 아니다
		assertThat(output.getAll()).contains("출력표지-anthropic");
		assertThat(output.getAll()).doesNotContain(KEY, "wxyz", "invalid x-api-key", "x-api-key", "req_011Ch24D");
	}

	@Test
	void aBrokenBodyIsUnexpected() {
		assertThat(probe(json(200, "{\"data\":"))).isInstanceOfSatisfying(ProbeResult.Failed.class, failed -> {
			assertThat(failed.kind()).isEqualTo(LlmFailureKind.UNEXPECTED);
			assertThat(failed.message()).startsWith("Anthropic 의 응답을 읽지 못했다 — ");
		});
	}

	@Test
	void doesNotFollowARedirectToAnotherHost() {
		elsewhere.stubFor(get(urlPathEqualTo("/v1/models")).willReturn(json(200, MODELS)));

		ProbeResult result = probe(
				aResponse().withStatus(302).withHeader("Location", elsewhere.baseUrl() + "/v1/models"));

		assertThat(result)
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.UNEXPECTED, "Anthropic 키 확인 요청이 실패했다 — 상태 302"));
		assertThat(server.getAllServeEvents()).hasSize(1);
		assertThat(elsewhere.getAllServeEvents()).isEmpty();
	}

	@Test
	void aBodyOverTheLimitIsUnexpected() {
		server.stubFor(get(urlPathEqualTo("/v1/models")).willReturn(json(200, MODELS)));
		int length = MODELS.getBytes().length;

		try (AnthropicProviderClient client = client(new JdkTransport(Duration.ofSeconds(5), length - 1))) {
			assertThat(client.probe()).isEqualTo(new ProbeResult.Failed(LlmFailureKind.UNEXPECTED,
					"LLM 공급자의 응답 본문이 상한(" + (length - 1) + " 바이트)을 넘었다"));
		}
		try (AnthropicProviderClient client = client(new JdkTransport(Duration.ofSeconds(5), length))) {
			assertThat(client.probe()).isEqualTo(new ProbeResult.Reachable());
		}
	}

	@Test
	void theProviderAddressIsACodeConstant() {
		assertThat(AnthropicProviderClient.BASE_URL).isEqualTo("https://api.anthropic.com");
	}

	@Test
	void aClosedClientSendsNothing() {
		server.stubFor(get(urlPathEqualTo("/v1/models")).willReturn(json(200, MODELS)));
		AnthropicProviderClient client = client();

		// 대조 — 닫기 전에는 같은 요청이 닿는다
		assertThat(client.probe()).isEqualTo(new ProbeResult.Reachable());
		assertThat(server.getAllServeEvents()).hasSize(1);

		client.close();
		client.close();

		assertThat(client.probe())
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.UNEXPECTED, "닫은 LLM 공급자 클라이언트에 요청했다"));
		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	@Test
	void theChatModelSendsWithTheSameKeyAndAddressAndStopsAfterClose(CapturedOutput output) {
		server.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(json(200, MESSAGE)));
		AnthropicProviderClient client = client();
		ChatModel model = client.chatModel("claude-x");
		System.out.println("출력표지-anthropic-chat");

		assertThat(model.call(new Prompt("ping")).getResult().getOutput().getText()).isEqualTo("pong");

		server.verify(postRequestedFor(urlPathEqualTo("/v1/messages")).withHeader("x-api-key", equalTo(KEY))
			.withHeader("Authorization", absent())
			.withRequestBody(matchingJsonPath("$.model", equalTo("claude-x"))));
		assertThat(server.getAllServeEvents()).hasSize(1);
		assertThat(output.getAll()).contains("출력표지-anthropic-chat");
		assertThat(output.getAll()).doesNotContain(KEY, "x-api-key");

		client.close();

		assertThatThrownBy(() -> model.call(new Prompt("ping")));
		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	@Test
	void theAsyncPathAuthorizesTheRequestAndStopsAfterClose() {
		server.stubFor(get(urlPathEqualTo("/v1/models")).willReturn(json(200, MODELS)));
		com.anthropic.backends.AnthropicBackend backend = com.anthropic.backends.AnthropicBackend.builder()
			.baseUrl(server.baseUrl())
			.apiKey(KEY)
			.build();
		AnthropicSdkHttpClient http = new AnthropicSdkHttpClient(new JdkTransport(), backend);
		com.anthropic.core.http.HttpRequest request = com.anthropic.core.http.HttpRequest.builder()
			.method(com.anthropic.core.http.HttpMethod.GET)
			.addPathSegment("v1")
			.addPathSegment("models")
			.build();

		assertThat(http.executeAsync(request, com.anthropic.core.RequestOptions.none()).join().statusCode())
			.isEqualTo(200);
		server.verify(getRequestedFor(urlPathEqualTo("/v1/models")).withHeader("x-api-key", equalTo(KEY))
			.withHeader("Authorization", absent()));
		assertThat(server.getAllServeEvents()).hasSize(1);

		http.close();

		assertThat(http.executeAsync(request, com.anthropic.core.RequestOptions.none()))
			.failsWithin(Duration.ofSeconds(5))
			.withThrowableOfType(java.util.concurrent.ExecutionException.class)
			.withCauseInstanceOf(TransportFailure.class)
			.withMessageContaining("닫은 LLM 공급자 클라이언트에 요청했다");
		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	private ProbeResult probe(ResponseDefinitionBuilder response) {
		server.resetAll();
		server.stubFor(get(urlPathEqualTo("/v1/models")).willReturn(response));
		try (AnthropicProviderClient client = client()) {
			return client.probe();
		}
	}

	private static AnthropicProviderClient client() {
		return client(new JdkTransport());
	}

	private static AnthropicProviderClient client(JdkTransport transport) {
		return new AnthropicProviderClient(server.baseUrl(), SecretValue.of(KEY), transport);
	}

	private static ResponseDefinitionBuilder json(int status, String body) {
		return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
	}

	/** Anthropic 의 오류 본문 꼴 */
	private static String error(String type, String message) {
		return "{\"type\":\"error\",\"error\":{\"type\":\"" + type + "\",\"message\":\"" + message
				+ "\"},\"request_id\":\"req_test\"}";
	}

}
