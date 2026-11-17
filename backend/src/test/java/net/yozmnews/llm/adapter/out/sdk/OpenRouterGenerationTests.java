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
 * OpenRouter 의 생성 요청 하나 — WireMock 을 공급자 자리에 두고, 요청에 실리는 라우팅 · 출력 상한 · 추론 강도 · 교집합 스키마, 응답의 본문 ·
 * 토큰 · 종료 사유, OpenRouter 꼴 오류(숫자 {@code code} · {@code metadata.error_type})의 분류, 200 안에 실린 오류, 출력에 키 · 프롬프트 ·
 * 응답 본문 · 검열에 걸린 입력 조각이 남지 않음을 본다. 오류 본문은 OpenRouter 문서의 예시와 2026-11-17 실측 꼴을 따온 것이다.
 * <p>
 * 지키는 규칙: llm-R8 · llm-R9 · llm-R21 · llm-R22 · llm-R23 · llm-R24
 */
@ExtendWith(OutputCaptureExtension.class)
class OpenRouterGenerationTests {

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String KEY = "sk-or-v1-wiremock-only-generation-wxyz";

	private static final String PATH = "/api/v1/chat/completions";

	private static final String CHAT_MODEL_LOGGER = "org.springframework.ai.openai.OpenAiChatModel";

	private static final String SCHEMA = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
			+ "\"type\":\"object\",\"properties\":{\"overview\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":1000},"
			+ "\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"maxItems\":3},"
			+ "\"mood\":{\"type\":\"string\",\"enum\":[\"good\",\"bad\"]}},"
			+ "\"required\":[\"overview\",\"tags\"],\"additionalProperties\":false}";

	private static final String ANSWER = completion("{\\\"overview\\\":\\\"응답표지-openrouter\\\"}", null, "stop");

	@AfterEach
	void resetTheChatModelLogger() {
		LoggingSystem.get(getClass().getClassLoader()).setLogLevel(CHAT_MODEL_LOGGER, null);
	}

	@Test
	void sendsTheRoutingOnEveryRequest() {
		server.stubFor(post(urlEqualTo(PATH)).willReturn(json(200, ANSWER)));

		generate(call(true, SCHEMA, ReasoningEffort.MEDIUM, 9_000));

		JsonNode body = sentBody();
		// 대조 — 같은 요청의 추론 강도는 OpenAI 꼴 그대로 실린다(라우팅만 빠져도 이 단언은 통과한다)
		assertThat(body.get("reasoning_effort").asString()).isEqualTo("medium");
		assertThat(body.get("provider")).isEqualTo(JSON.readTree("""
				{"data_collection":"deny","require_parameters":true}
				"""));
	}

	@Test
	void sendsTheBudgetTheEffortAndTheCommonSchema() {
		server.stubFor(post(urlEqualTo(PATH)).willReturn(json(200, ANSWER)));

		generate(call(true, SCHEMA, ReasoningEffort.HIGH, 9_000));

		JsonNode body = sentBody();
		assertThat(body.get("model").asString()).isEqualTo("anthropic/claude-x");
		assertThat(body.get("max_completion_tokens").asInt()).isEqualTo(9_000);
		assertThat(body.get("reasoning_effort").asString()).isEqualTo("high");
		assertThat(body.get("messages").get(0).get("content").asString()).isEqualTo("지시표지-openrouter");
		assertThat(body.get("messages").get(1).get("content").asString()).isEqualTo("사용자표지-openrouter");
		JsonNode format = body.get("response_format");
		assertThat(format.get("type").asString()).isEqualTo("json_schema");
		assertThat(format.get("json_schema").get("schema")).isEqualTo(JSON.readTree("""
				{"type":"object","properties":{
				  "overview":{"type":"string"},
				  "tags":{"type":"array","items":{"type":"string"}},
				  "mood":{"enum":["good","bad",null]}},
				 "required":["overview","tags","mood"],"additionalProperties":false}
				"""));
	}

	@Test
	void aModelWithoutStructuredOutputGetsNoSchemaButTheRouting() {
		server.stubFor(post(urlEqualTo(PATH)).willReturn(json(200, ANSWER)));

		generate(call(false, SCHEMA, ReasoningEffort.LOW, 4_100));

		JsonNode body = sentBody();
		assertThat(body.has("response_format")).isFalse();
		assertThat(body.get("provider").get("data_collection").asString()).isEqualTo("deny");
	}

	@Test
	void sendsNoAppAttributionHeaders() {
		server.stubFor(post(urlEqualTo(PATH)).willReturn(json(200, ANSWER)));

		generate(call(true, SCHEMA, ReasoningEffort.LOW, 4_100));

		var headers = server.getAllServeEvents().get(0).getRequest().getHeaders();
		assertThat(headers.getHeader("HTTP-Referer").isPresent()).isFalse();
		assertThat(headers.getHeader("X-OpenRouter-Title").isPresent()).isFalse();
		assertThat(headers.getHeader("X-Title").isPresent()).isFalse();
		// 대조 — 키는 실린다
		assertThat(headers.getHeader("Authorization").values()).containsExactly("Bearer " + KEY);
	}

	@Test
	void readsTheBodyTheTokensAndTheStop() {
		assertThat(answer(json(200, ANSWER))).isEqualTo(new GenerationOutcome.Answered(
				"{\"overview\":\"응답표지-openrouter\"}", 12, 34, GenerationStop.COMPLETE));
		assertThat(answer(json(200, completion("{\\\"overview\\\":", null, "length"))))
			.isEqualTo(new GenerationOutcome.Answered("{\"overview\":", 12, 34, GenerationStop.TRUNCATED));
		assertThat(answer(json(200, completion(null, "요청을 도울 수 없다", "content_filter"))))
			.isEqualTo(new GenerationOutcome.Answered("", 12, 34, GenerationStop.REFUSED));
	}

	@Test
	void sortsANumericCodeErrorByTheStatus() {
		// OpenRouter 꼴 — code 가 숫자이고 type 이 없다
		assertThat(answer(json(402, openRouterError(402, "Insufficient credits", "payment_required"))))
			.isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.QUOTA_EXHAUSTED,
					"OpenRouter 생성 요청이 실패했다 — 상태 402", null, false));
	}

	@Test
	void sortsAStringCodeErrorTheSameWay() {
		// 대조 — 같은 402 에 문자열 code · type 이 든 본문(OpenAI 꼴)도 같은 종류다. 숫자 code 만 비켜 간 이웃 입력이다
		assertThat(answer(json(402, "{\"error\":{\"message\":\"Insufficient credits\",\"type\":\"billing\","
				+ "\"param\":null,\"code\":\"insufficient_credits\"}}")))
			.isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.QUOTA_EXHAUSTED,
					"OpenRouter 생성 요청이 실패했다 — 상태 402", null, false));
	}

	@Test
	void sortsFailuresByTheStatusAndTheErrorType() {
		assertThat(answer(json(401, "{\"error\":{\"message\":\"User not found.\",\"code\":401}}")))
			.isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.INVALID_KEY,
					"OpenRouter 생성 요청이 실패했다 — 상태 401", null, false));
		assertThat(answer(json(403, openRouterError(403, "Blocked by guardrail", "permission_denied"))))
			.isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.PERMISSION_DENIED,
					"OpenRouter 생성 요청이 실패했다 — 상태 403", null, false));
		assertThat(answer(json(403, openRouterError(403, "The model refused", "refusal"))))
			.isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.UNEXPECTED,
					"OpenRouter 생성 요청이 실패했다 — 상태 403", null, false));
		assertThat(answer(json(429, openRouterError(429, "Rate limit exceeded", "rate_limit_exceeded"))
			.withHeader("retry-after", "7")))
			.isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.TRANSIENT,
					"OpenRouter 생성 요청이 실패했다 — 상태 429", Duration.ofSeconds(7), false));
		assertThat(answer(json(502, openRouterError(502, "Provider returned error", "provider_unavailable"))))
			.isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.TRANSIENT,
					"OpenRouter 생성 요청이 실패했다 — 상태 502", null, false));
		assertThat(answer(json(404, GUARDRAIL_404)))
			.isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.UNEXPECTED,
					"OpenRouter 생성 요청이 실패했다 — 상태 404", null, false));
	}

	@Test
	void anErrorInsideAnOkAnswerIsUnexpected() {
		// 200 · finish_reason "error" — 생성이 시작된 뒤 공급자가 끊었다
		assertThat(answer(json(200, "{\"id\":\"gen-1\",\"object\":\"chat.completion\",\"created\":1,"
				+ "\"model\":\"anthropic/claude-x\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
				+ "\"content\":\"partial output\"},\"finish_reason\":\"error\",\"error\":{\"code\":502,"
				+ "\"message\":\"Provider disconnected\",\"metadata\":{\"error_type\":\"provider_unavailable\"}}}],"
				+ "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":3,\"total_tokens\":15}}")))
			.isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.UNEXPECTED,
					"OpenRouter 생성 응답의 종료 사유를 모른다", null, false));
		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	@Test
	void anOkAnswerHoldingOnlyAnErrorIsUnexpected() {
		GenerationOutcome outcome = answer(json(200,
				"{\"id\":\"gen-1\",\"error\":{\"code\":502,\"message\":\"Provider disconnected\"}}"));

		assertThat(outcome).isInstanceOfSatisfying(GenerationOutcome.Failed.class, failed -> {
			assertThat(failed.kind()).isEqualTo(LlmFailureKind.UNEXPECTED);
			assertThat(failed.retryAfter()).isNull();
			assertThat(failed.deadlinePassed()).isFalse();
		});
		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	@Test
	void keepsTheKeyThePromptTheBodyAndTheFlaggedInputOutOfMessagesAndOutput(CapturedOutput output) {
		System.out.println("출력표지-openrouter-generation");

		GenerationOutcome ok = answer(json(200, ANSWER));
		GenerationOutcome flagged = answer(json(403, "{\"error\":{\"code\":403,\"message\":\"Input flagged sk-or-v1-wxyz\","
				+ "\"metadata\":{\"reasons\":[\"violence\"],\"flagged_input\":\"검열표지-openrouter\","
				+ "\"provider_name\":\"x\",\"model_slug\":\"anthropic/claude-x\"}}}"));

		assertThat(ok).isInstanceOf(GenerationOutcome.Answered.class);
		assertThat(flagged).isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.UNEXPECTED,
				"OpenRouter 생성 요청이 실패했다 — 상태 403", null, false));
		// 대조 — 출력이 캡처된다
		assertThat(output.getAll()).contains("출력표지-openrouter-generation");
		assertThat(output.getAll()).doesNotContain(KEY, "wxyz", "지시표지-openrouter", "사용자표지-openrouter",
				"응답표지-openrouter", "검열표지-openrouter", "Input flagged", "Authorization");
	}

	@Test
	void anEmptyAnswerIsUnexpectedAndTheSilencedLoggerKeepsThePromptOut(CapturedOutput output) {
		LoggingSystem.get(getClass().getClassLoader()).setLogLevel(CHAT_MODEL_LOGGER, LogLevel.OFF);

		assertThat(answer(json(200, empty()))).isEqualTo(new GenerationOutcome.Failed(LlmFailureKind.UNEXPECTED,
				"OpenRouter 생성 응답에 내용이 없다", null, false));
		assertThat(output.getAll()).doesNotContain("지시표지-openrouter", "사용자표지-openrouter");
	}

	@Test
	void theChatModelWouldLogThePromptOnAnEmptyAnswerIfItsLoggerWereOn(CapturedOutput output) {
		// 대조 — 로거를 끄지 않으면 OpenRouter 의 내용 없는 응답에서도 프롬프트가 경고 로그에 남는다(위 시험의 부정 단언이 헛돌지 않는다)
		LoggingSystem.get(getClass().getClassLoader()).setLogLevel(CHAT_MODEL_LOGGER, LogLevel.WARN);

		answer(json(200, empty()));

		assertThat(output.getAll()).contains("사용자표지-openrouter");
	}

	@Test
	void sendsOnceOnAServerError() {
		answer(json(503, openRouterError(503, "Provider overloaded", "provider_overloaded")));

		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	/** 모델을 막는 가드레일에 OpenRouter 가 준 본문 꼴(2026-11-17 실측 — 문구는 바꿨다) */
	private static final String GUARDRAIL_404 = "{\"error\":{\"code\":404,\"message\":\"No endpoints\","
			+ "\"metadata\":{\"failed_routing_step\":\"Filter by Guardrails\",\"input_endpoint_count\":3,"
			+ "\"ineligibility_reasons\":[{\"reason\":\"model-ignored-by-guardrail\",\"endpoint_count\":3}]}}}";

	private GenerationOutcome answer(ResponseDefinitionBuilder response) {
		server.resetAll();
		server.stubFor(post(urlEqualTo(PATH)).willReturn(response));
		return generate(call(true, SCHEMA, ReasoningEffort.LOW, 4_100));
	}

	private static GenerationOutcome generate(GenerationCall call) {
		try (OpenRouterProviderClient client = new OpenRouterProviderClient(server.baseUrl() + "/api/v1",
				SecretValue.of(KEY), new JdkTransport(Duration.ofSeconds(5)))) {
			return client.generate(call);
		}
	}

	private static GenerationCall call(boolean structuredOutput, String schema, ReasoningEffort effort, int tokens) {
		return new GenerationCall("anthropic/claude-x", structuredOutput, "지시표지-openrouter", "사용자표지-openrouter",
				schema, effort, tokens);
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
		return "{\"id\":\"gen-1\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"anthropic/claude-x\","
				+ "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":"
				+ ((escapedContent != null) ? "\"" + escapedContent + "\"" : "null") + ",\"refusal\":"
				+ ((refusal != null) ? "\"" + refusal + "\"" : "null") + "},\"finish_reason\":\"" + finishReason + "\"}],"
				+ "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":34,\"total_tokens\":46}}";
	}

	private static String empty() {
		return "{\"id\":\"gen-1\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"anthropic/claude-x\","
				+ "\"choices\":[],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":0,\"total_tokens\":12}}";
	}

	/** OpenRouter 의 오류 본문 꼴 — code 는 숫자, 종류 이름은 metadata.error_type */
	private static String openRouterError(int code, String message, String errorType) {
		return "{\"error\":{\"code\":" + code + ",\"message\":\"" + message + "\",\"metadata\":{\"error_type\":\""
				+ errorType + "\"}}}";
	}

}
