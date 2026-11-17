package net.yozmnews.llm.adapter.out.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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
 * Anthropic 의 생성 요청 하나 — WireMock 을 공급자 자리에 두고, 요청에 실리는 출력 상한 · 추론 강도 · 맞춘 스키마, 응답의 본문(텍스트
 * 블록만) · 토큰 · 종료 사유, 실패의 종류 · 기다릴 시간 · 마감 초과, 출력에 키 · 프롬프트 · 응답 본문이 남지 않음을 본다. 응답은 Messages
 * API 의 실제 꼴을 따온 대체 응답이다.
 * <p>
 * 지키는 규칙: llm-R8 · llm-R9 · llm-R12 · llm-R21 · llm-R22 · llm-R23 · llm-R24
 */
@ExtendWith(OutputCaptureExtension.class)
class AnthropicGenerationTests {

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String KEY = "sk-ant-wiremock-only-generation-wxyz";

	private static final String CHAT_MODEL_LOGGER = "org.springframework.ai.anthropic.AnthropicChatModel";

	private static final String SCHEMA = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
			+ "\"type\":\"object\",\"properties\":{\"overview\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":1000},"
			+ "\"refs\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"maxItems\":20},"
			+ "\"note\":{\"type\":[\"string\",\"null\"]}},\"required\":[\"overview\",\"refs\"],"
			+ "\"additionalProperties\":false}";

	private static final String TEXT = "{\\\"overview\\\":\\\"응답표지-anthropic\\\",\\\"refs\\\":[]}";

	@AfterEach
	void resetTheChatModelLogger() {
		LoggingSystem.get(getClass().getClassLoader()).setLogLevel(CHAT_MODEL_LOGGER, null);
	}

	@Test
	void sendsTheBudgetTheEffortAndTheSupportedSchema() {
		server.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(json(200, message(text(TEXT), "end_turn"))));

		generate(call(true, SCHEMA, ReasoningEffort.HIGH, 18_000));

		JsonNode body = sentBody();
		assertThat(body.get("model").asString()).isEqualTo("claude-x");
		assertThat(body.get("max_tokens").asInt()).isEqualTo(18_000);
		assertThat(body.has("thinking")).isFalse();
		assertThat(body.has("temperature")).isFalse();
		assertThat(body.get("system").toString()).contains("지시표지-anthropic");
		assertThat(body.get("messages").get(0).get("role").asString()).isEqualTo("user");
		assertThat(body.get("messages").get(0).toString()).contains("사용자표지-anthropic");
		JsonNode config = body.get("output_config");
		assertThat(config.get("effort").asString()).isEqualTo("high");
		assertThat(config.get("format").get("type").asString()).isEqualTo("json_schema");
		assertThat(config.get("format").get("schema")).isEqualTo(JSON.readTree("""
				{"type":"object","properties":{"overview":{"type":"string"},
				  "refs":{"type":"array","items":{"type":"string"}},"note":{"type":["string","null"]}},
				 "required":["overview","refs"],"additionalProperties":false}
				"""));
	}

	@Test
	void aModelWithoutStructuredOutputGetsNoSchema() {
		server.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(json(200, message(text(TEXT), "end_turn"))));

		generate(call(false, SCHEMA, ReasoningEffort.LOW, 4_100));

		JsonNode config = sentBody().get("output_config");
		assertThat(config.has("format")).isFalse();
		// 대조 — 추론 강도는 그대로 실린다
		assertThat(config.get("effort").asString()).isEqualTo("low");
	}

	@Test
	void readsOnlyTheTextBlocksTheTokensAndTheStop() {
		// 추론 블록의 문장은 본문에 섞이지 않는다 — Spring AI 가 추론 블록을 뒤의 따로 된 생성으로 붙인다
		String thinkingThenText = "{\"type\":\"thinking\",\"thinking\":\"추론표지-anthropic\",\"signature\":\"sig\"},"
				+ text(TEXT);

		assertThat(answer(json(200, message(thinkingThenText, "end_turn")))).isEqualTo(new GenerationOutcome.Answered(
				"{\"overview\":\"응답표지-anthropic\",\"refs\":[]}", 12, 34, GenerationStop.COMPLETE));
		assertThat(answer(json(200, message(text("{\\\"overview\\\":"), "max_tokens"))))
			.isEqualTo(new GenerationOutcome.Answered("{\"overview\":", 12, 34, GenerationStop.TRUNCATED));
		assertThat(answer(json(200, message(text(""), "refusal"))))
			.isEqualTo(new GenerationOutcome.Answered("", 12, 34, GenerationStop.REFUSED));
	}

	@Test
	void anUnknownStopIsUnexpected() {
		assertThat(answer(json(200, message(text("{}"), "pause_turn")))).isEqualTo(new GenerationOutcome.Failed(
				LlmFailureKind.UNEXPECTED, "Anthropic 생성 응답의 종료 사유를 모른다", null, false));
	}

	@Test
	void sortsFailuresWithTheSameRulesAsTheKeyCheckAndKeepsTheWait() {
		assertThat(answer(json(401, error("authentication_error", "invalid x-api-key"))))
			.isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.INVALID_KEY,
					"Anthropic 생성 요청이 실패했다 — 상태 401", null, false));
		assertThat(answer(json(403, error("permission_error", "Your API key does not have permission"))))
			.isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.PERMISSION_DENIED,
					"Anthropic 생성 요청이 실패했다 — 상태 403", null, false));
		assertThat(answer(json(429, error("rate_limit_error", "Number of requests has exceeded your rate limit"))
			.withHeader("retry-after", "7")))
			.isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.TRANSIENT, "Anthropic 생성 요청이 실패했다 — 상태 429",
					Duration.ofSeconds(7), false));
		assertThat(answer(json(429, error("rate_limit_error", "monthly spend limit reached"))))
			.isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.QUOTA_EXHAUSTED,
					"Anthropic 생성 요청이 실패했다 — 상태 429", null, false));
		assertThat(answer(json(404, error("not_found_error", "model: claude-x"))))
			.isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.UNEXPECTED,
					"Anthropic 생성 요청이 실패했다 — 상태 404", null, false));
	}

	@Test
	void sendsOnceOnAServerError() {
		assertThat(answer(json(529, error("overloaded_error", "Overloaded")))).isEqualTo(new GenerationOutcome.Failed(
				LlmFailureKind.TRANSIENT, "Anthropic 생성 요청이 실패했다 — 상태 529", null, false));

		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	@Test
	void aLateAnswerPassesTheDeadline() {
		server.resetAll();
		server.stubFor(post(urlPathEqualTo("/v1/messages"))
			.willReturn(json(200, message(text(TEXT), "end_turn")).withFixedDelay(2_000)));

		try (AnthropicProviderClient client = client(new JdkTransport(Duration.ofMillis(300)))) {
			assertThat(client.generate(call(true, SCHEMA, ReasoningEffort.LOW, 4_100))).isEqualTo(
					new GenerationOutcome.Failed(LlmFailureKind.TRANSIENT, "LLM 공급자 응답 마감(300ms)을 넘었다", null, true));
		}
	}

	@Test
	void keepsTheKeyThePromptAndTheBodyOutOfMessagesAndOutput(CapturedOutput output) {
		System.out.println("출력표지-anthropic-generation");

		GenerationOutcome ok = answer(json(200, message(text(TEXT), "end_turn")));
		GenerationOutcome failed = answer(json(401, error("authentication_error", "invalid x-api-key")));

		assertThat(ok).isInstanceOf(GenerationOutcome.Answered.class);
		assertThat(failed).isInstanceOfSatisfying(GenerationOutcome.Failed.class,
				result -> assertThat(result.message()).doesNotContain(KEY, "invalid x-api-key"));
		// 대조 — 출력이 캡처된다
		assertThat(output.getAll()).contains("출력표지-anthropic-generation");
		assertThat(output.getAll()).doesNotContain(KEY, "wxyz", "지시표지-anthropic", "사용자표지-anthropic",
				"응답표지-anthropic", "invalid x-api-key", "x-api-key");
	}

	@Test
	void anEmptyAnswerIsUnexpectedAndTheSilencedLoggerKeepsThePromptOut(CapturedOutput output) {
		LoggingSystem.get(getClass().getClassLoader()).setLogLevel(CHAT_MODEL_LOGGER, LogLevel.OFF);

		assertThat(answer(json(200, message("", "refusal")))).isEqualTo(new GenerationOutcome.Failed(
				LlmFailureKind.UNEXPECTED, "Anthropic 생성 응답에 내용이 없다", null, false));
		assertThat(output.getAll()).doesNotContain("지시표지-anthropic", "사용자표지-anthropic");
	}

	@Test
	void theChatModelWouldLogThePromptOnAnEmptyAnswerIfItsLoggerWereOn(CapturedOutput output) {
		// 대조 — 로거를 끄지 않으면 프롬프트가 경고 로그에 남는다(로거를 끄는 설정이 지키는 것이 실재한다)
		LoggingSystem.get(getClass().getClassLoader()).setLogLevel(CHAT_MODEL_LOGGER, LogLevel.WARN);

		answer(json(200, message("", "refusal")));

		assertThat(output.getAll()).contains("사용자표지-anthropic");
	}

	private GenerationOutcome answer(ResponseDefinitionBuilder response) {
		server.resetAll();
		server.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(response));
		return generate(call(true, SCHEMA, ReasoningEffort.LOW, 4_100));
	}

	private static GenerationOutcome generate(GenerationCall call) {
		try (AnthropicProviderClient client = client(new JdkTransport(Duration.ofSeconds(5)))) {
			return client.generate(call);
		}
	}

	private static GenerationCall call(boolean structuredOutput, String schema, ReasoningEffort effort, int tokens) {
		return new GenerationCall("claude-x", structuredOutput, "지시표지-anthropic", "사용자표지-anthropic", schema,
				effort, tokens);
	}

	private static AnthropicProviderClient client(JdkTransport transport) {
		return new AnthropicProviderClient(server.baseUrl(), SecretValue.of(KEY), transport);
	}

	private static JsonNode sentBody() {
		assertThat(server.getAllServeEvents()).hasSize(1);
		return JSON.readTree(server.getAllServeEvents().get(0).getRequest().getBodyAsString());
	}

	private static ResponseDefinitionBuilder json(int status, String body) {
		return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
	}

	/** 텍스트 블록 하나 — 본문은 JSON 안의 문자열로 이스케이프한 것 */
	private static String text(String escaped) {
		return "{\"type\":\"text\",\"text\":\"" + escaped + "\"}";
	}

	/** Messages API 응답 꼴 */
	private static String message(String blocks, String stopReason) {
		return "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-x\",\"content\":["
				+ blocks + "],\"stop_reason\":\"" + stopReason + "\",\"stop_sequence\":null,"
				+ "\"usage\":{\"input_tokens\":12,\"output_tokens\":34}}";
	}

	/** Anthropic 의 오류 본문 꼴 */
	private static String error(String type, String message) {
		return "{\"type\":\"error\",\"error\":{\"type\":\"" + type + "\",\"message\":\"" + message
				+ "\"},\"request_id\":\"req_test\"}";
	}

}
