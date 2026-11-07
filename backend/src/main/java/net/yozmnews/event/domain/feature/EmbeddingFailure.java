package net.yozmnews.event.domain.feature;

import java.util.Objects;

/**
 * 예상한 이유로 임베딩하지 못했다 — docs/specs/event.md §4.
 *
 * @param kind 실패 갈래
 * @param message 운영자용 문구 — 비밀 값 · 응답 본문 · 입력 텍스트를 담지 않는다
 */
public record EmbeddingFailure(EmbedderFailureKind kind, String message) implements EmbeddingResult {

	/**
	 * @throws IllegalArgumentException 문구가 공백뿐일 때
	 */
	public EmbeddingFailure {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(message, "message");
		if (message.isBlank()) {
			throw new IllegalArgumentException("실패 문구가 비었다");
		}
	}

}
