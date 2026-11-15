package net.yozmnews.llm.domain.failure;

import java.util.Set;

import net.yozmnews.llm.LlmFailureKind;

/**
 * OpenAI 오류를 실패 종류로 가르는 정본 — docs/specs/llm.md §4 · llm-R8.
 * <p>
 * 요청 제한과 한도 소진이 같은 429 라 오류의 {@code code} · {@code type} 까지 읽는다.
 */
public final class OpenAiErrors {

	/** 한도 소진을 뜻하는 429 의 {@code code} */
	private static final Set<String> QUOTA_CODES = Set.of("credit_balance_exhausted", "organization_spend_limit_exceeded",
			"project_spend_limit_exceeded", "organization_usage_limit_exceeded");

	/** 한도 소진을 뜻하는 429 의 {@code type} */
	private static final String QUOTA_TYPE = "insufficient_quota";

	private OpenAiErrors() {
	}

	public static LlmFailureKind classify(ProviderError error) {
		return switch (error.status()) {
			case 401 -> LlmFailureKind.INVALID_KEY;
			case 403 -> LlmFailureKind.PERMISSION_DENIED;
			case 429 -> quota(error) ? LlmFailureKind.QUOTA_EXHAUSTED : LlmFailureKind.TRANSIENT;
			default -> error.retriableStatus() ? LlmFailureKind.TRANSIENT : LlmFailureKind.UNEXPECTED;
		};
	}

	private static boolean quota(ProviderError error) {
		return (error.code() != null && QUOTA_CODES.contains(error.code())) || QUOTA_TYPE.equals(error.type());
	}

}
