package net.yozmnews.evaluation;

import java.util.List;
import java.util.Objects;

/**
 * 임베딩 성공 — 넘긴 텍스트마다 벡터 하나, 넘긴 순서 · docs/specs/evaluation.md §3.
 * <p>
 * {@link TextEmbedding} 이 낸 성공은 넘긴 텍스트 수만큼, 밝힌 차원의 벡터다.
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
