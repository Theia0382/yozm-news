package net.yozmnews.evaluation.domain.openai;

import net.yozmnews.evaluation.EmbedderFailureKind;

/**
 * OpenAI 호환 임베딩 API 의 2xx 가 아닌 상태 코드 갈래의 정본 — docs/specs/evaluation.md §4 {@code domain/openai} · ADR-0092.
 */
public final class OpenAiEmbeddingErrors {

	private OpenAiEmbeddingErrors() {
	}

	/**
	 * 400 · 413 · 422 → 입력 거부, 401 · 403 · 404 → 임베더 사용 불가, 408 · 409 · 429 · 5xx → 일시, 그 밖(3xx · 4xx) → 임베더 사용 불가.
	 * @param status 2xx 가 아닌 HTTP 상태 코드
	 */
	public static EmbedderFailureKind classify(int status) {
		return switch (status) {
			case 400, 413, 422 -> EmbedderFailureKind.INPUT_REJECTED;
			case 401, 403, 404 -> EmbedderFailureKind.EMBEDDER_UNAVAILABLE;
			case 408, 409, 429 -> EmbedderFailureKind.TRANSIENT;
			default -> (status >= 500 && status <= 599) ? EmbedderFailureKind.TRANSIENT
					: EmbedderFailureKind.EMBEDDER_UNAVAILABLE;
		};
	}

}
