package net.yozmnews.llm.domain.failure;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.llm.LlmFailureKind;
import org.junit.jupiter.api.Test;

/**
 * 공급자 오류의 실패 종류 — 공급자마다 상태 코드와 오류의 종류(`code` · `type` · OpenRouter 의 `metadata.error_type`),
 * `retry-after` 헤더 유무로 가른다.
 * <p>
 * 지키는 규칙: llm-R8
 */
class ProviderErrorsTests {

	@Test
	void openAiSortsAuthenticationAndPermission() {
		assertThat(openAi(401, "invalid_api_key", "invalid_request_error")).isEqualTo(LlmFailureKind.INVALID_KEY);
		assertThat(openAi(401, null, null)).isEqualTo(LlmFailureKind.INVALID_KEY);
		assertThat(openAi(403, null, null)).isEqualTo(LlmFailureKind.PERMISSION_DENIED);
	}

	@Test
	void openAiTellsQuotaFromRateLimitByTheErrorCodeOrType() {
		assertThat(openAi(429, "credit_balance_exhausted", null)).isEqualTo(LlmFailureKind.QUOTA_EXHAUSTED);
		assertThat(openAi(429, "organization_spend_limit_exceeded", null)).isEqualTo(LlmFailureKind.QUOTA_EXHAUSTED);
		assertThat(openAi(429, "project_spend_limit_exceeded", null)).isEqualTo(LlmFailureKind.QUOTA_EXHAUSTED);
		assertThat(openAi(429, "organization_usage_limit_exceeded", null)).isEqualTo(LlmFailureKind.QUOTA_EXHAUSTED);
		assertThat(openAi(429, null, "insufficient_quota")).isEqualTo(LlmFailureKind.QUOTA_EXHAUSTED);
		assertThat(openAi(429, "slow_down", "rate_limit_error")).isEqualTo(LlmFailureKind.TRANSIENT);
		assertThat(openAi(429, null, null)).isEqualTo(LlmFailureKind.TRANSIENT);
	}

	@Test
	void openAiIgnoresAQuotaCodeOnAnotherStatus() {
		assertThat(openAi(400, "credit_balance_exhausted", "insufficient_quota")).isEqualTo(LlmFailureKind.UNEXPECTED);
	}

	@Test
	void openAiSortsTheRest() {
		assertThat(openAi(408, null, null)).isEqualTo(LlmFailureKind.TRANSIENT);
		assertThat(openAi(409, null, null)).isEqualTo(LlmFailureKind.TRANSIENT);
		assertThat(openAi(500, null, null)).isEqualTo(LlmFailureKind.TRANSIENT);
		assertThat(openAi(503, "server_is_overloaded", "service_unavailable_error")).isEqualTo(LlmFailureKind.TRANSIENT);
		assertThat(openAi(599, null, null)).isEqualTo(LlmFailureKind.TRANSIENT);
		assertThat(openAi(302, null, null)).isEqualTo(LlmFailureKind.UNEXPECTED);
		assertThat(openAi(400, null, null)).isEqualTo(LlmFailureKind.UNEXPECTED);
		assertThat(openAi(404, null, null)).isEqualTo(LlmFailureKind.UNEXPECTED);
		assertThat(openAi(499, null, null)).isEqualTo(LlmFailureKind.UNEXPECTED);
		assertThat(openAi(600, null, null)).isEqualTo(LlmFailureKind.UNEXPECTED);
	}

	@Test
	void anthropicSortsAuthenticationPermissionAndBilling() {
		assertThat(anthropic(401, "authentication_error", false)).isEqualTo(LlmFailureKind.INVALID_KEY);
		assertThat(anthropic(403, "permission_error", false)).isEqualTo(LlmFailureKind.PERMISSION_DENIED);
		assertThat(anthropic(402, "billing_error", false)).isEqualTo(LlmFailureKind.QUOTA_EXHAUSTED);
	}

	@Test
	void anthropicTellsTheSpendCapFromRateLimitByTheRetryAfterHeader() {
		assertThat(anthropic(429, "rate_limit_error", false)).isEqualTo(LlmFailureKind.QUOTA_EXHAUSTED);
		assertThat(anthropic(429, "rate_limit_error", true)).isEqualTo(LlmFailureKind.TRANSIENT);
	}

	@Test
	void anthropicSortsTheRest() {
		assertThat(anthropic(408, null, false)).isEqualTo(LlmFailureKind.TRANSIENT);
		assertThat(anthropic(409, "conflict_error", false)).isEqualTo(LlmFailureKind.TRANSIENT);
		assertThat(anthropic(500, "api_error", false)).isEqualTo(LlmFailureKind.TRANSIENT);
		assertThat(anthropic(529, "overloaded_error", false)).isEqualTo(LlmFailureKind.TRANSIENT);
		assertThat(anthropic(599, null, true)).isEqualTo(LlmFailureKind.TRANSIENT);
		assertThat(anthropic(302, null, false)).isEqualTo(LlmFailureKind.UNEXPECTED);
		assertThat(anthropic(400, "invalid_request_error", false)).isEqualTo(LlmFailureKind.UNEXPECTED);
		assertThat(anthropic(404, "not_found_error", false)).isEqualTo(LlmFailureKind.UNEXPECTED);
		assertThat(anthropic(499, null, false)).isEqualTo(LlmFailureKind.UNEXPECTED);
		assertThat(anthropic(600, null, false)).isEqualTo(LlmFailureKind.UNEXPECTED);
	}

	@Test
	void openRouterSortsByTheStatusAndAPermissionMark() {
		assertThat(openRouter(401, "authentication")).isEqualTo(LlmFailureKind.INVALID_KEY);
		assertThat(openRouter(401, null)).isEqualTo(LlmFailureKind.INVALID_KEY);
		assertThat(openRouter(402, "payment_required")).isEqualTo(LlmFailureKind.QUOTA_EXHAUSTED);
		assertThat(openRouter(403, "permission_denied")).isEqualTo(LlmFailureKind.PERMISSION_DENIED);
	}

	@Test
	void openRouterKeepsAContentBlockAndAnUnmarked403AwayFromThePermissionKind() {
		assertThat(openRouter(403, "refusal")).isEqualTo(LlmFailureKind.UNEXPECTED);
		assertThat(openRouter(403, "content_policy_violation")).isEqualTo(LlmFailureKind.UNEXPECTED);
		assertThat(openRouter(403, null)).isEqualTo(LlmFailureKind.UNEXPECTED);
		assertThat(openRouter(403, "PERMISSION_DENIED")).isEqualTo(LlmFailureKind.UNEXPECTED);
	}

	@Test
	void openRouterSortsTheRest() {
		assertThat(openRouter(429, "rate_limit_exceeded")).isEqualTo(LlmFailureKind.TRANSIENT);
		assertThat(openRouter(429, null)).isEqualTo(LlmFailureKind.TRANSIENT);
		assertThat(openRouter(408, "timeout")).isEqualTo(LlmFailureKind.TRANSIENT);
		assertThat(openRouter(409, null)).isEqualTo(LlmFailureKind.TRANSIENT);
		assertThat(openRouter(500, "server")).isEqualTo(LlmFailureKind.TRANSIENT);
		assertThat(openRouter(502, "provider_unavailable")).isEqualTo(LlmFailureKind.TRANSIENT);
		assertThat(openRouter(503, "provider_overloaded")).isEqualTo(LlmFailureKind.TRANSIENT);
		assertThat(openRouter(599, null)).isEqualTo(LlmFailureKind.TRANSIENT);
		assertThat(openRouter(302, null)).isEqualTo(LlmFailureKind.UNEXPECTED);
		assertThat(openRouter(400, "invalid_request")).isEqualTo(LlmFailureKind.UNEXPECTED);
		assertThat(openRouter(404, "not_found")).isEqualTo(LlmFailureKind.UNEXPECTED);
		assertThat(openRouter(404, null)).isEqualTo(LlmFailureKind.UNEXPECTED);
		assertThat(openRouter(499, null)).isEqualTo(LlmFailureKind.UNEXPECTED);
		assertThat(openRouter(600, null)).isEqualTo(LlmFailureKind.UNEXPECTED);
	}

	@Test
	void openRouterReadsTheErrorTypeFromTheErrorMetadata() {
		// 문서의 예시 본문
		assertThat(OpenRouterErrors.errorType("""
				{"error":{"code":429,"message":"Rate limit exceeded","metadata":{"error_type":"rate_limit_exceeded"}}}
				""")).isEqualTo("rate_limit_exceeded");
		assertThat(OpenRouterErrors.errorType("""
				{"error":{"code":403,"message":"blocked","metadata":{"error_type":"refusal","provider_code":"bio_policy"}}}
				""")).isEqualTo("refusal");
		// error 마디 없이 오류 객체만 넘어온 꼴
		assertThat(OpenRouterErrors.errorType("""
				{"code":402,"message":"Insufficient credits","metadata":{"error_type":"payment_required"}}
				""")).isEqualTo("payment_required");
	}

	@Test
	void openRouterHasNoErrorTypeWhenTheBodyCarriesNone() {
		// 틀린 키에 OpenRouter 가 준 본문(2026-11-17 실측) — metadata 가 없다
		assertThat(OpenRouterErrors.errorType("{\"error\":{\"message\":\"User not found.\",\"code\":401}}")).isNull();
		// 모델을 막는 가드레일의 본문(2026-11-17 실측 꼴) — metadata 는 라우팅 정보다
		assertThat(OpenRouterErrors.errorType("""
				{"error":{"code":404,"message":"x","metadata":{"failed_routing_step":"Filter by Guardrails"}}}
				""")).isNull();
		assertThat(OpenRouterErrors.errorType("{\"error\":{\"metadata\":{\"error_type\":403}}}")).isNull();
		assertThat(OpenRouterErrors.errorType("{\"error\":{\"metadata\":\"refusal\"}}")).isNull();
		assertThat(OpenRouterErrors.errorType("{\"error\":")).isNull();
		assertThat(OpenRouterErrors.errorType("not json")).isNull();
		assertThat(OpenRouterErrors.errorType("[]")).isNull();
		assertThat(OpenRouterErrors.errorType("")).isNull();
	}

	private static LlmFailureKind openAi(int status, String code, String type) {
		return OpenAiErrors.classify(new ProviderError(status, code, type, false));
	}

	private static LlmFailureKind anthropic(int status, String type, boolean retryAfter) {
		return AnthropicErrors.classify(new ProviderError(status, null, type, retryAfter));
	}

	private static LlmFailureKind openRouter(int status, String errorType) {
		return OpenRouterErrors.classify(new ProviderError(status, null, errorType, false));
	}

}
