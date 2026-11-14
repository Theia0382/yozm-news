package net.yozmnews.llm.domain.failure;

import net.yozmnews.llm.LlmFailureKind;

/**
 * Anthropic 오류를 실패 종류로 가르는 정본 — docs/specs/llm.md §4 · llm-R8.
 * <p>
 * 요청 제한과 사용 등급의 월 지출 상한이 같은 429 다 — 지출 상한의 429 에는 {@code retry-after} 헤더가 없다.
 */
public final class AnthropicErrors {

	private AnthropicErrors() {
	}

	public static LlmFailureKind classify(ProviderError error) {
		return switch (error.status()) {
			case 401 -> LlmFailureKind.INVALID_KEY;
			case 403 -> LlmFailureKind.PERMISSION_DENIED;
			case 402 -> LlmFailureKind.QUOTA_EXHAUSTED;
			case 429 -> error.retryAfter() ? LlmFailureKind.TRANSIENT : LlmFailureKind.QUOTA_EXHAUSTED;
			default -> error.retriableStatus() ? LlmFailureKind.TRANSIENT : LlmFailureKind.UNEXPECTED;
		};
	}

}
