package net.yozmnews.event.domain.feature;

/**
 * 임베딩 한 번의 결과(성공 · 예상한 실패) — docs/specs/event.md §4
 */
public sealed interface EmbeddingResult permits Embeddings, EmbeddingFailure {

}
