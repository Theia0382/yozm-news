package net.yozmnews.llm.adapter.out.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import net.yozmnews.llm.GenerationStop;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.llm.domain.provider.GenerationCall;
import net.yozmnews.llm.domain.provider.GenerationOutcome;
import net.yozmnews.platform.SecretValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI 의 생성 요청 하나 — WireMock 을 공급자 자리에 두고, 요청에 실리는 출력 상한 · 추론 강도 · 맞춘 스키마, 응답의 본문 · 토큰 · 종료
 * 사유, 실패의 종류 · 기다릴 시간 · 마감 초과, 출력에 키 · 프롬프트 · 응답 본문이 남지 않음을 본다. 응답은 Chat Completions 의 실제 꼴을
 * 따온 대체 응답이다.
 * <p>
 * 지키는 규칙: llm-R8 · llm-R9 · llm-R12 · llm-R21 · llm-R22 · llm-R23 · llm-R24
 */
@ExtendWith(OutputCaptureExtension.class)
class OpenAiGenerationTests {

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String KEY = "sk-wiremock-only-generation-wxyz";

	private static final String CHAT_MODEL_LOGGER = "org.springframework.ai.openai.OpenAiChatModel";

	private static final String SCHEMA = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
			+ "\"type\":\"object\",\"properties\":{\"overview\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":1000},"
			+ "\"note\":{\"type\":[\"string\",\"null\"]}},\"required\":[\"overview\"],\"additionalProperties\":false}";

	private static final String ANSWER = completion("{\\\"overview\\\":\\\"응답표지-openai\\\"}", null, "stop");

	@AfterEach
	void resetTheChatModelLogger() {
		LoggingSystem.get(getClass().getClassLoader()).setLogLevel(CHAT_MODEL_LOGGER, null);
	}

	@Test
	void sendsTheBudgetTheEffortAndTheStrictSchema() {
		server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(json(200, ANSWER)));

		generate(call(true, SCHEMA, ReasoningEffort.MEDIUM, 9_000));

		JsonNode body = sentBody();
		assertThat(body.get("model").asString()).isEqualTo("gpt-x");
		assertThat(body.get("max_completion_tokens").asInt()).isEqualTo(9_000);
		assertThat(body.has("max_tokens")).isFalse();
		assertThat(body.get("reasoning_effort").asString()).isEqualTo("medium");
		assertThat(body.has("temperature")).isFalse();
		assertThat(body.get("messages").get(0).get("role").asString()).isEqualTo("system");
		assertThat(body.get("messages").get(0).get("content").asString()).isEqualTo("지시표지-openai");
		assertThat(body.get("messages").get(1).get("role").asString()).isEqualTo("user");
		assertThat(body.get("messages").get(1).get("content").asString()).isEqualTo("사용자표지-openai");
		JsonNode format = body.get("response_format");
		assertThat(format.get("type").asString()).isEqualTo("json_schema");
		assertThat(format.get("json_schema").get("strict").asBoolean()).isTrue();
		assertThat(format.get("json_schema").get("schema")).isEqualTo(JSON.readTree("""
				{"type":"object","properties":{"overview":{"type":"string"},"note":{"type":["string","null"]}},
				 "required":["overview","note"],"additionalProperties":false}
				"""));
	}

	@Test
	void aModelWithoutStructuredOutputGetsNoSchema() {
		server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(json(200, ANSWER)));

		generate(call(false, SCHEMA, ReasoningEffort.LOW, 4_100));

		JsonNode body = sentBody();
		assertThat(body.has("response_format")).isFalse();
		// 대조 — 추론 강도 · 출력 상한은 그대로 실린다
		assertThat(body.get("reasoning_effort").asString()).isEqualTo("low");
		assertThat(body.get("max_completion_tokens").asInt()).isEqualTo(4_100);
	}

	@Test
	void aCallWithoutASchemaGetsNoResponseFormat() {
		server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(json(200, ANSWER)));

		generate(call(true, null, ReasoningEffort.LOW, 4_100));

		assertThat(sentBody().has("response_format")).isFalse();
	}

	@Test
	void readsTheBodyTheTokensAndTheStop() {
		assertThat(answer(json(200, ANSWER))).isEqualTo(new GenerationOutcome.Answered(
				"{\"overview\":\"응답표지-openai\"}", 12, 34, GenerationStop.COMPLETE));
		assertThat(answer(json(200, completion("{\\\"overview\\\":", null, "length"))))
			.isEqualTo(new GenerationOutcome.Answered("{\"overview\":", 12, 34, GenerationStop.TRUNCATED));
		assertThat(answer(json(200, completion(null, "요청을 도울 수 없다", "stop"))))
			.isEqualTo(new GenerationOutcome.Answered("", 12, 34, GenerationStop.REFUSED));
	}

	@Test
	void anUnknownStopIsUnexpected() {
		assertThat(answer(json(200, completion("{}", null, "tool_calls")))).isEqualTo(new GenerationOutcome.Failed(
				LlmFailureKind.UNEXPECTED, "OpenAI 생성 응답의 종료 사유를 모른다", null, false));
	}

	@Test
	void sortsFailuresWithTheSameRulesAsTheKeyCheckAndKeepsTheWait() {
		assertThat(answer(json(401, error("Incorrect API key provided: sk-wirem***wxyz", "invalid_request_error",
				"invalid_api_key"))))
			.isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.INVALID_KEY, "OpenAI 생성 요청이 실패했다 — 상태 401",
					null, false));
		assertThat(answer(json(403, error("Country, region, or territory not supported", "request_forbidden", null))))
			.isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.PERMISSION_DENIED,
					"OpenAI 생성 요청이 실패했다 — 상태 403", null, false));
		assertThat(answer(json(429, error("Rate limit reached for requests", "rate_limit_error", null))
			.withHeader("retry-after", "7")))
			.isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.TRANSIENT, "OpenAI 생성 요청이 실패했다 — 상태 429",
					Duration.ofSeconds(7), false));
		assertThat(answer(json(429, error("Credit balance exhausted", "insufficient_quota", "credit_balance_exhausted"))))
			.isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.QUOTA_EXHAUSTED,
					"OpenAI 생성 요청이 실패했다 — 상태 429", null, false));
		assertThat(answer(json(400, error("maximum context length", "invalid_request_error", null))))
			.isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.UNEXPECTED, "OpenAI 생성 요청이 실패했다 — 상태 400",
					null, false));
	}

	@Test
	void sendsOnceOnAServerError() {
		assertThat(answer(json(500, error("The server had an error", "server_error", null)))).isEqualTo(
				new GenerationOutcome.Failed(LlmFailureKind.TRANSIENT, "OpenAI 생성 요청이 실패했다 — 상태 500", null, false));

		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	@Test
	void aLateAnswerPassesTheDeadline() {
		server.resetAll();
		server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(json(200, ANSWER).withFixedDelay(2_000)));

		try (OpenAiProviderClient client = client(new JdkTransport(Duration.ofMillis(300)))) {
			assertThat(client.generate(call(true, SCHEMA, ReasoningEffort.LOW, 4_100))).isEqualTo(
					new GenerationOutcome.Failed(LlmFailureKind.TRANSIENT, "LLM 공급자 응답 마감(300ms)을 넘었다", null, true));
		}
	}

	@Test
	void keepsTheKeyThePromptAndTheBodyOutOfMessagesAndOutput(CapturedOutput output) {
		System.out.println("출력표지-openai-generation");

		GenerationOutcome ok = answer(json(200, ANSWER));
		GenerationOutcome failed = answer(json(401, error("Incorrect API key provided: sk-wirem***wxyz",
				"invalid_request_error", "invalid_api_key")));

		assertThat(ok).isInstanceOf(GenerationOutcome.Answered.class);
		assertThat(failed).isInstanceOfSatisfying(GenerationOutcome.Failed.class,
				result -> assertThat(result.message()).doesNotContain(KEY, "wxyz", "Incorrect API key"));
		// 대조 — 출력이 캡처된다
		assertThat(output.getAll()).contains("출력표지-openai-generation");
		assertThat(output.getAll()).doesNotContain(KEY, "wxyz", "지시표지-openai", "사용자표지-openai",
				"응답표지-openai", "Incorrect API key", "Authorization");
	}

	@Test
	void anEmptyAnswerIsUnexpectedAndTheSilencedLoggerKeepsThePromptOut(CapturedOutput output) {
		LoggingSystem.get(getClass().getClassLoader()).setLogLevel(CHAT_MODEL_LOGGER, LogLevel.OFF);

		assertThat(answer(json(200, empty()))).isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.UNEXPECTED,
				"OpenAI 생성 응답에 내용이 없다", null, false));
		assertThat(output.getAll()).doesNotContain("지시표지-openai", "사용자표지-openai");
	}

	@Test
	void theChatModelWouldLogThePromptOnAnEmptyAnswerIfItsLoggerWereOn(CapturedOutput output) {
		// 대조 — 로거를 끄지 않으면 프롬프트가 경고 로그에 남는다(로거를 끄는 설정이 지키는 것이 실재한다)
		LoggingSystem.get(getClass().getClassLoader()).setLogLevel(CHAT_MODEL_LOGGER, LogLevel.WARN);

		answer(json(200, empty()));

		assertThat(output.getAll()).contains("사용자표지-openai");
	}

	private GenerationOutcome answer(ResponseDefinitionBuilder response) {
		server.resetAll();
		server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(response));
		return generate(call(true, SCHEMA, ReasoningEffort.LOW, 4_100));
	}

	private static GenerationOutcome generate(GenerationCall call) {
		try (OpenAiProviderClient client = client(new JdkTransport(Duration.ofSeconds(5)))) {
			return client.generate(call);
		}
	}

	private static GenerationCall call(boolean structuredOutput, String schema, ReasoningEffort effort, int tokens) {
		return new GenerationCall("gpt-x", structuredOutput, "지시표지-openai", "사용자표지-openai", schema, effort, tokens);
	}

	private static OpenAiProviderClient client(JdkTransport transport) {
		return new OpenAiProviderClient(server.baseUrl() + "/v1", SecretValue.of(KEY), transport);
	}

	private static JsonNode sentBody() {
		assertThat(server.getAllServeEvents()).hasSize(1);
		return JSON.readTree(server.getAllServeEvents().get(0).getRequest().getBodyAsString());
	}

	private static ResponseDefinitionBuilder json(int status, String body) {
		return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
	}

	/** Chat Completions 응답 꼴 — 본문(JSON 안의 문자열로 이스케이프한 것)이나 거절 문구 하나와 종료 사유 */
	private static String completion(String escapedContent, String refusal, String finishReason) {
		return "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"gpt-x\","
				+ "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":"
				+ ((escapedContent != null) ? "\"" + escapedContent + "\"" : "null") + ",\"refusal\":"
				+ ((refusal != null) ? "\"" + refusal + "\"" : "null") + "},\"finish_reason\":\"" + finishReason + "\"}],"
				+ "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":34,\"total_tokens\":46}}";
	}

	private static String empty() {
		return "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"gpt-x\",\"choices\":[],"
				+ "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":0,\"total_tokens\":12}}";
	}

	/** OpenAI 의 오류 본문 꼴 */
	private static String error(String message, String type, String code) {
		return "{\"error\":{\"message\":\"" + message + "\",\"type\":\"" + type + "\",\"param\":null,\"code\":"
				+ ((code != null) ? "\"" + code + "\"" : "null") + "}}";
	}

}
