package net.yozmnews.llm.adapter.out.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.llm.domain.provider.GenerationCall;
import net.yozmnews.llm.domain.provider.GenerationOutcome;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.platform.SecretValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 키 하나의 OpenRouter 클라이언트 — WireMock 을 공급자 자리에 두고, 확인 요청이 모델 목록이 아니라 키 조회로 가는 것, 키가 실리는 헤더,
 * 환경 변수의 주소 · 키를 읽지 않음, 실패 종류와 문구, 출력에 키 · 응답 본문이 남지 않음, 리다이렉트를 따르지 않음, 닫은 뒤에는 보내지 않음을
 * 본다.
 * <p>
 * 지키는 규칙: llm-R6 · llm-R8 · llm-R9 · llm-R11 · llm-R13 · llm-R14
 */
@ExtendWith(OutputCaptureExtension.class)
class OpenRouterProviderClientTests {

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	/** 리다이렉트가 가리키는 다른 호스트 자리 */
	@RegisterExtension
	static WireMockExtension elsewhere = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	private static final String KEY = "sk-or-v1-wiremock-only-0123456789wxyz";

	/** 키 조회의 정상 응답 꼴 — 본문은 읽지 않는다 */
	private static final String KEY_INFO = "{\"data\":{\"label\":\"sk-or-v1-wir...wxyz\",\"limit\":null,\"usage\":1.5,"
			+ "\"is_free_tier\":false}}";

	/** 모델 목록 — OpenRouter 는 인증 없이도 200 으로 답한다(2026-11-17 실측) */
	private static final String MODELS = "{\"data\":[{\"id\":\"openai/gpt-x\"}]}";

	/** 틀린 키에 OpenRouter 가 준 본문(2026-11-17 실측) */
	private static final String UNKNOWN_KEY = "{\"error\":{\"message\":\"User not found.\",\"code\":401}}";

	@Test
	void theTestJvmCarriesTheEnvironmentThatMustNotBeUsed() {
		// 아래 시험들의 전제 — 이 값들이 없으면 "환경 변수를 읽지 않는다"는 확인이 헛돈다. 생성은 OpenAI SDK 로 보낸다
		assertThat(System.getenv("OPENAI_BASE_URL")).isEqualTo("http://127.0.0.1:9");
		assertThat(System.getenv("OPENAI_API_KEY")).isEqualTo("env-key-must-not-be-used");
	}

	@Test
	void probesTheKeyEndpointNotTheModelList() {
		server.stubFor(get(urlEqualTo("/api/v1/models")).willReturn(json(200, MODELS)));
		server.stubFor(get(urlEqualTo("/api/v1/key")).willReturn(json(401, UNKNOWN_KEY)));

		try (OpenRouterProviderClient client = client()) {
			assertThat(client.probe()).isEqualTo(
					new ProbeResult.Failed(LlmFailureKind.INVALID_KEY, "OpenRouter 키 확인 요청이 실패했다 — 상태 401"));
		}

		assertThat(server.getAllServeEvents()).hasSize(1);
		server.verify(getRequestedFor(urlEqualTo("/api/v1/key")).withHeader("Authorization", equalTo("Bearer " + KEY)));
		server.verify(0, getRequestedFor(urlEqualTo("/api/v1/models")));
	}

	@Test
	void aWrongKeyIsInvalidWhenTheModelListAlsoWantsAKey() {
		// 대조 — 모델 목록도 키를 요구하는 이웃 조건에서는 어느 엔드포인트로 확인해도 틀린 키가 INVALID_KEY 다. 위 시험의 실패가 목록이
		// 인증 없이 열려 있다는 조건에서만 나는 것임을 보인다
		server.stubFor(get(urlEqualTo("/api/v1/models")).willReturn(json(401, UNKNOWN_KEY)));
		server.stubFor(get(urlEqualTo("/api/v1/key")).willReturn(json(401, UNKNOWN_KEY)));

		try (OpenRouterProviderClient client = client()) {
			assertThat(client.probe()).isEqualTo(
					new ProbeResult.Failed(LlmFailureKind.INVALID_KEY, "OpenRouter 키 확인 요청이 실패했다 — 상태 401"));
		}
	}

	@Test
	void aKnownKeyIsReachableWithTheKeyOnce() {
		server.stubFor(get(urlEqualTo("/api/v1/key")).willReturn(json(200, KEY_INFO)));

		try (OpenRouterProviderClient client = client()) {
			assertThat(client.probe()).isEqualTo(new ProbeResult.Reachable());
		}

		assertThat(server.getAllServeEvents()).hasSize(1);
		var headers = server.getAllServeEvents().get(0).getRequest().getHeaders();
		assertThat(headers.getHeader("Authorization").values()).containsExactly("Bearer " + KEY);
		assertThat(headers.getHeader("HTTP-Referer").isPresent()).isFalse();
		assertThat(headers.getHeader("X-OpenRouter-Title").isPresent()).isFalse();
	}

	@Test
	void sortsProviderErrorsAndSaysOnlyTheStatus() {
		assertThat(probe(json(402, "{\"error\":{\"code\":402,\"message\":\"Insufficient credits\","
				+ "\"metadata\":{\"error_type\":\"payment_required\"}}}"))).isEqualTo(
						new ProbeResult.Failed(LlmFailureKind.QUOTA_EXHAUSTED, "OpenRouter 키 확인 요청이 실패했다 — 상태 402"));
		assertThat(probe(json(403, "{\"error\":{\"code\":403,\"message\":\"Forbidden\","
				+ "\"metadata\":{\"error_type\":\"permission_denied\"}}}"))).isEqualTo(new ProbeResult.Failed(
						LlmFailureKind.PERMISSION_DENIED, "OpenRouter 키 확인 요청이 실패했다 — 상태 403"));
		assertThat(probe(json(429, "{\"error\":{\"code\":429,\"message\":\"Rate limit exceeded\"}}")
			.withHeader("retry-after", "3")))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.TRANSIENT, "OpenRouter 키 확인 요청이 실패했다 — 상태 429"));
		assertThat(probe(json(500, "{\"error\":{\"code\":500,\"message\":\"Internal Server Error\"}}")))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.TRANSIENT, "OpenRouter 키 확인 요청이 실패했다 — 상태 500"));
		assertThat(probe(aResponse().withStatus(404).withBody("not json")))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.UNEXPECTED, "OpenRouter 키 확인 요청이 실패했다 — 상태 404"));
	}

	@Test
	void doesNotRetryAFailedRequest() {
		probe(json(500, "{\"error\":{\"code\":500,\"message\":\"Internal Server Error\"}}"));

		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	@Test
	void keepsTheKeyAndTheResponseBodyOutOfMessagesAndOutput(CapturedOutput output) {
		System.out.println("출력표지-openrouter");

		ProbeResult result = probe(json(401, "{\"error\":{\"message\":\"Invalid key sk-or-v1-wir...wxyz\",\"code\":401}}"));

		assertThat(result).isInstanceOfSatisfying(ProbeResult.Failed.class,
				failed -> assertThat(failed.message()).doesNotContain(KEY, "wxyz", "Invalid key"));
		// 대조 — 출력이 캡처된다. 아래의 부정 단언이 빈 출력 때문에 통과하는 것이 아니다
		assertThat(output.getAll()).contains("출력표지-openrouter");
		assertThat(output.getAll()).doesNotContain(KEY, "wxyz", "Invalid key", "Authorization");
	}

	@Test
	void doesNotFollowARedirectToAnotherHost() {
		elsewhere.stubFor(get(urlEqualTo("/api/v1/key")).willReturn(json(200, KEY_INFO)));

		ProbeResult result = probe(
				aResponse().withStatus(302).withHeader("Location", elsewhere.baseUrl() + "/api/v1/key"));

		assertThat(result)
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.UNEXPECTED, "OpenRouter 키 확인 요청이 실패했다 — 상태 302"));
		assertThat(server.getAllServeEvents()).hasSize(1);
		assertThat(elsewhere.getAllServeEvents()).isEmpty();
	}

	@Test
	void aLateAnswerIsTransient() {
		server.stubFor(get(urlEqualTo("/api/v1/key")).willReturn(json(200, KEY_INFO).withFixedDelay(2_000)));

		try (OpenRouterProviderClient client = client(new JdkTransport(Duration.ofMillis(300), 1024))) {
			assertThat(client.probe())
				.isEqualTo(new ProbeResult.Failed(LlmFailureKind.TRANSIENT, "LLM 공급자 응답 마감(300ms)을 넘었다"));
		}
	}

	@Test
	void theProductionAddressIsTheOpenRouterApi() {
		assertThat(OpenRouterProviderClient.BASE_URL).isEqualTo("https://openrouter.ai/api/v1");
	}

	@Test
	void aClosedClientSendsNothingOnEitherPath() {
		server.stubFor(get(urlEqualTo("/api/v1/key")).willReturn(json(200, KEY_INFO)));
		OpenRouterProviderClient client = client();

		// 대조 — 닫기 전에는 같은 요청이 닿는다
		assertThat(client.probe()).isEqualTo(new ProbeResult.Reachable());
		assertThat(server.getAllServeEvents()).hasSize(1);

		client.close();
		client.close();

		assertThat(client.probe())
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.UNEXPECTED, "닫은 LLM 공급자 클라이언트에 요청했다"));
		assertThat(client.generate(new GenerationCall("openai/gpt-x", false, "지시", "사용자", null, ReasoningEffort.LOW,
				100))).isInstanceOfSatisfying(GenerationOutcome.Failed.class,
						failed -> assertThat(failed.message()).isEqualTo("닫은 LLM 공급자 클라이언트에 요청했다"));
		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	@Test
	void generationGoesToTheGivenAddressWithTheGivenKey() {
		server.stubFor(post(urlEqualTo("/api/v1/chat/completions")).willReturn(json(200,
				"{\"id\":\"gen-1\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"openai/gpt-x\","
						+ "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"pong\"},"
						+ "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,"
						+ "\"total_tokens\":2}}")));

		try (OpenRouterProviderClient client = client()) {
			assertThat(client.generate(new GenerationCall("openai/gpt-x", false, "지시", "사용자", null,
					ReasoningEffort.LOW, 100))).isInstanceOf(GenerationOutcome.Answered.class);
		}

		// 환경 변수의 주소(127.0.0.1:9) · 키가 아니라 넘긴 주소 · 키로 갔다
		assertThat(server.getAllServeEvents()).hasSize(1);
		assertThat(server.getAllServeEvents().get(0).getRequest().getHeaders().getHeader("Authorization").values())
			.containsExactly("Bearer " + KEY);
	}

	private ProbeResult probe(ResponseDefinitionBuilder response) {
		server.resetAll();
		server.stubFor(get(urlEqualTo("/api/v1/key")).willReturn(response));
		try (OpenRouterProviderClient client = client()) {
			return client.probe();
		}
	}

	private static OpenRouterProviderClient client() {
		return client(new JdkTransport(Duration.ofSeconds(5)));
	}

	private static OpenRouterProviderClient client(JdkTransport transport) {
		return new OpenRouterProviderClient(server.baseUrl() + "/api/v1", SecretValue.of(KEY), transport);
	}

	private static ResponseDefinitionBuilder json(int status, String body) {
		return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
	}

}
