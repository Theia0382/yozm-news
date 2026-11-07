package net.yozmnews.event.domain.feature;

import java.util.List;
import java.util.Objects;

/**
 * 임베딩 성공 — 넘긴 텍스트마다 벡터 하나, 넘긴 순서 · docs/specs/event.md §4.
 * <p>
 * 수 · 차원이 맞는지는 부르는 쪽이 {@link EmbeddingCheck} 로 확인한다.
 *
 * @param vectors 벡터 — 복사해 받는다
 */
public record Embeddings(List<Embedding> vectors) implements EmbeddingResult {

	/**
	 * @throws NullPointerException 목록이나 원소가 null 일 때
	 */
	public Embeddings {
		vectors = List.copyOf(Objects.requireNonNull(vectors, "vectors"));
	}

}
