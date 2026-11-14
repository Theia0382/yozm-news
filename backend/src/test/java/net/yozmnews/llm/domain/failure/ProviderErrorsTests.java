package net.yozmnews.llm.domain.failure;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.llm.LlmFailureKind;
import org.junit.jupiter.api.Test;

/**
 * 공급자 오류의 실패 종류 — 공급자마다 상태 코드와 오류의 종류(`code` · `type`), `retry-after` 헤더 유무로 가른다.
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

	private static LlmFailureKind openAi(int status, String code, String type) {
		return OpenAiErrors.classify(new ProviderError(status, code, type, false));
	}

	private static LlmFailureKind anthropic(int status, String type, boolean retryAfter) {
		return AnthropicErrors.classify(new ProviderError(status, null, type, retryAfter));
	}

}
