package net.yozmnews.llm.adapter.out.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
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
 * 키 하나의 OpenAI 클라이언트 — WireMock 을 공급자 자리에 두고 키가 실리는 헤더, 환경 변수의 주소 · 키를 읽지 않음, 실패 종류와 문구,
 * 출력에 키 · 응답 본문이 남지 않음, 전송의 방어(리다이렉트 · 본문 상한 · 마감), 닫기를 본다. 응답은 공급자의 실제 응답 꼴 · 공식 문서의
 * 오류 형식을 따온 대체 응답이다.
 * <p>
 * 지키는 규칙: llm-R6 · llm-R7 · llm-R8 · llm-R9 · llm-R11 · llm-R12 · llm-R13 · llm-R14
 */
@ExtendWith(OutputCaptureExtension.class)
class OpenAiProviderClientTests {

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	/** 리다이렉트가 가리키는 다른 호스트 자리 */
	@RegisterExtension
	static WireMockExtension elsewhere = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	private static final String KEY = "sk-wiremock-only-0123456789abcdwxyz";

	private static final String MODELS = "{\"object\":\"list\",\"data\":[{\"id\":\"gpt-x\",\"object\":\"model\","
			+ "\"created\":1,\"owned_by\":\"openai\"}]}";

	/** 틀린 키에 OpenAI 가 주는 실제 본문 꼴 — 키의 접두와 끝 4자리를 되돌려준다 */
	private static final String BAD_KEY = "{\"error\":{\"message\":\"Incorrect API key provided: "
			+ "sk-wirem***********************wxyz. You can find your API key at "
			+ "https://platform.openai.com/account/api-keys.\",\"type\":\"invalid_request_error\",\"param\":null,"
			+ "\"code\":\"invalid_api_key\"}}";

	private static final String CHAT = "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"created\":1,"
			+ "\"model\":\"gpt-x\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"pong\"},"
			+ "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}";

	@Test
	void theTestJvmCarriesTheEnvironmentThatMustNotBeUsed() {
		// 아래 시험들의 전제 — 이 값들이 없으면 "환경 변수를 읽지 않는다" · "로그를 꺼 둔다"는 확인이 헛돈다
		assertThat(System.getenv("OPENAI_BASE_URL")).isEqualTo("http://127.0.0.1:9");
		assertThat(System.getenv("OPENAI_API_KEY")).isEqualTo("env-key-must-not-be-used");
		assertThat(System.getenv("OPENAI_LOG")).isEqualTo("debug");
	}

	@Test
	void listsModelsWithTheGivenKeyAtTheGivenAddress() {
		server.stubFor(get(urlEqualTo("/v1/models")).willReturn(json(200, MODELS)));

		try (OpenAiProviderClient client = client()) {
			assertThat(client.probe()).isEqualTo(new ProbeResult.Reachable());
		}

		assertThat(server.getAllServeEvents()).hasSize(1);
		server.verify(getRequestedFor(urlEqualTo("/v1/models")).withHeader("Authorization", equalTo("Bearer " + KEY)));
		// 키는 한 번만 실린다
		assertThat(server.getAllServeEvents().get(0).getRequest().getHeaders().getHeader("Authorization").values())
			.containsExactly("Bearer " + KEY);
	}

	@Test
	void sortsProviderErrorsAndSaysOnlyTheStatus() {
		assertThat(probe(json(401, BAD_KEY)))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.INVALID_KEY, "OpenAI 키 확인 요청이 실패했다 — 상태 401"));
		assertThat(probe(json(403, error("Country, region, or territory not supported", "request_forbidden", null))))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.PERMISSION_DENIED, "OpenAI 키 확인 요청이 실패했다 — 상태 403"));
		assertThat(probe(json(429, error("Credit balance exhausted", "insufficient_quota", "credit_balance_exhausted"))))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.QUOTA_EXHAUSTED, "OpenAI 키 확인 요청이 실패했다 — 상태 429"));
		assertThat(probe(json(429, error("Rate limit reached for requests", "rate_limit_error", null))))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.TRANSIENT, "OpenAI 키 확인 요청이 실패했다 — 상태 429"));
		assertThat(probe(json(500, error("The server had an error", "server_error", null))))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.TRANSIENT, "OpenAI 키 확인 요청이 실패했다 — 상태 500"));
		assertThat(probe(json(404, error("Not found", "invalid_request_error", null))))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.UNEXPECTED, "OpenAI 키 확인 요청이 실패했다 — 상태 404"));
	}

	@Test
	void doesNotRetryAFailedRequest() {
		probe(json(500, error("The server had an error", "server_error", null)));

		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	@Test
	void keepsTheKeyAndTheResponseBodyOutOfMessagesAndOutput(CapturedOutput output) {
		System.out.println("출력표지-openai");

		ProbeResult result = probe(json(401, BAD_KEY));

		assertThat(result).isInstanceOfSatisfying(ProbeResult.Failed.class,
				failed -> assertThat(failed.message()).doesNotContain(KEY, "wxyz", "Incorrect API key"));
		// 대조 — 출력이 캡처된다. 아래의 부정 단언이 빈 출력 때문에 통과하는 것이 아니다
		assertThat(output.getAll()).contains("출력표지-openai");
		assertThat(output.getAll()).doesNotContain(KEY, "wxyz", "Incorrect API key", "Authorization");
	}

	@Test
	void aBrokenBodyIsUnexpected() {
		assertThat(probe(json(200, "{\"object\":\"list\",\"data\":"))).isInstanceOfSatisfying(ProbeResult.Failed.class,
				failed -> {
					assertThat(failed.kind()).isEqualTo(LlmFailureKind.UNEXPECTED);
					assertThat(failed.message()).startsWith("OpenAI 의 응답을 읽지 못했다 — ");
				});
	}

	@Test
	void doesNotFollowARedirectToAnotherHost() {
		elsewhere.stubFor(get(urlEqualTo("/v1/models")).willReturn(json(200, MODELS)));

		ProbeResult result = probe(
				aResponse().withStatus(302).withHeader("Location", elsewhere.baseUrl() + "/v1/models"));

		assertThat(result)
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.UNEXPECTED, "OpenAI 키 확인 요청이 실패했다 — 상태 302"));
		assertThat(server.getAllServeEvents()).hasSize(1);
		assertThat(elsewhere.getAllServeEvents()).isEmpty();
	}

	@Test
	void aBodyOverTheLimitIsUnexpectedAndABodyOfTheLimitIsRead() {
		server.stubFor(get(urlEqualTo("/v1/models")).willReturn(json(200, MODELS)));
		int length = MODELS.getBytes().length;

		try (OpenAiProviderClient client = client(new JdkTransport(Duration.ofSeconds(5), length - 1))) {
			assertThat(client.probe()).isEqualTo(new ProbeResult.Failed(LlmFailureKind.UNEXPECTED,
					"LLM 공급자의 응답 본문이 상한(" + (length - 1) + " 바이트)을 넘었다"));
		}
		try (OpenAiProviderClient client = client(new JdkTransport(Duration.ofSeconds(5), length))) {
			assertThat(client.probe()).isEqualTo(new ProbeResult.Reachable());
		}
	}

	@Test
	void theProductionLimitsAreFiveAndFifteenSecondsAndOneMebibyte() {
		assertThat(JdkTransport.CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(5));
		assertThat(JdkTransport.REQUEST_DEADLINE).isEqualTo(Duration.ofSeconds(15));
		assertThat(JdkTransport.MAX_RESPONSE_BYTES).isEqualTo(1_048_576);
		assertThat(OpenAiProviderClient.BASE_URL).isEqualTo("https://api.openai.com/v1");
	}

	@Test
	void aLateAnswerIsTransient() {
		server.stubFor(get(urlEqualTo("/v1/models")).willReturn(json(200, MODELS).withFixedDelay(2_000)));

		try (OpenAiProviderClient client = client(new JdkTransport(Duration.ofMillis(300), 1024))) {
			assertThat(client.probe())
				.isEqualTo(new ProbeResult.Failed(LlmFailureKind.TRANSIENT, "LLM 공급자 응답 마감(300ms)을 넘었다"));
		}
	}

	@Test
	void theSameAnswerWithoutDelayIsReadUnderTheShortDeadline() {
		server.stubFor(get(urlEqualTo("/v1/models")).willReturn(json(200, MODELS)));

		try (OpenAiProviderClient client = client(new JdkTransport(Duration.ofMillis(300), 1024))) {
			assertThat(client.probe()).isEqualTo(new ProbeResult.Reachable());
		}
	}

	@Test
	void aRefusedConnectionIsTransient() throws IOException {
		int port;
		try (ServerSocket socket = new ServerSocket(0)) {
			port = socket.getLocalPort();
		}

		try (OpenAiProviderClient client = new OpenAiProviderClient("http://127.0.0.1:" + port + "/v1",
				SecretValue.of(KEY), new JdkTransport())) {
			assertThat(client.probe())
				.isEqualTo(new ProbeResult.Failed(LlmFailureKind.TRANSIENT, "LLM 공급자와 주고받지 못했다 — ConnectException"));
		}
	}

	@Test
	void aClosedClientSendsNothing() {
		server.stubFor(get(urlEqualTo("/v1/models")).willReturn(json(200, MODELS)));
		OpenAiProviderClient client = client();

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
		server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(json(200, CHAT)));
		OpenAiProviderClient client = client();
		ChatModel model = client.chatModel("gpt-x");
		System.out.println("출력표지-openai-chat");

		assertThat(model.call(new Prompt("ping")).getResult().getOutput().getText()).isEqualTo("pong");

		server.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
			.withHeader("Authorization", equalTo("Bearer " + KEY))
			.withRequestBody(matchingJsonPath("$.model", equalTo("gpt-x"))));
		assertThat(server.getAllServeEvents()).hasSize(1);
		assertThat(output.getAll()).contains("출력표지-openai-chat");
		assertThat(output.getAll()).doesNotContain(KEY, "Authorization");

		client.close();

		assertThatThrownBy(() -> model.call(new Prompt("ping")));
		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	@Test
	void sendsNoAnthropicHeader() {
		server.stubFor(get(urlEqualTo("/v1/models")).willReturn(json(200, MODELS)));

		try (OpenAiProviderClient client = client()) {
			client.probe();
		}

		server.verify(getRequestedFor(urlEqualTo("/v1/models")).withHeader("x-api-key", absent()));
	}

	@Test
	void theAsyncPathSendsTheSameRequestAndStopsAfterClose() throws IOException {
		server.stubFor(get(urlEqualTo("/v1/models")).willReturn(json(200, MODELS)));
		OpenAiSdkHttpClient http = new OpenAiSdkHttpClient(new JdkTransport());
		com.openai.core.http.HttpRequest request = com.openai.core.http.HttpRequest.builder()
			.method(com.openai.core.http.HttpMethod.GET)
			.baseUrl(server.baseUrl())
			.addPathSegment("v1")
			.addPathSegment("models")
			.putHeader("Authorization", "Bearer " + KEY)
			.build();

		com.openai.core.http.HttpResponse response = http.executeAsync(request, com.openai.core.RequestOptions.none())
			.join();

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(new String(response.body().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
			.isEqualTo(MODELS);
		server.verify(getRequestedFor(urlEqualTo("/v1/models")).withHeader("Authorization", equalTo("Bearer " + KEY)));
		assertThat(server.getAllServeEvents()).hasSize(1);

		http.close();

		assertThat(http.executeAsync(request, com.openai.core.RequestOptions.none())).failsWithin(Duration.ofSeconds(5))
			.withThrowableOfType(java.util.concurrent.ExecutionException.class)
			.withCauseInstanceOf(TransportFailure.class)
			.withMessageContaining("닫은 LLM 공급자 클라이언트에 요청했다");
		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	private ProbeResult probe(ResponseDefinitionBuilder response) {
		server.resetAll();
		server.stubFor(get(urlEqualTo("/v1/models")).willReturn(response));
		try (OpenAiProviderClient client = client()) {
			return client.probe();
		}
	}

	private static OpenAiProviderClient client() {
		return client(new JdkTransport());
	}

	private static OpenAiProviderClient client(JdkTransport transport) {
		return new OpenAiProviderClient(server.baseUrl() + "/v1", SecretValue.of(KEY), transport);
	}

	private static ResponseDefinitionBuilder json(int status, String body) {
		return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
	}

	/** OpenAI 의 오류 본문 꼴 */
	private static String error(String message, String type, String code) {
		return "{\"error\":{\"message\":\"" + message + "\",\"type\":\"" + type + "\",\"param\":null,\"code\":"
				+ ((code != null) ? "\"" + code + "\"" : "null") + "}}";
	}

}
