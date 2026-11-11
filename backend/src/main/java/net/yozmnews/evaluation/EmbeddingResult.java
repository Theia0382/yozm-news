package net.yozmnews.evaluation;

/**
 * 임베딩 한 번의 결과(성공 · 예상한 실패) — docs/specs/evaluation.md §3
 */
public sealed interface EmbeddingResult permits Embeddings, EmbeddingFailure {

}
