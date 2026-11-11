package net.yozmnews.evaluation.domain.embedder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.evaluation.EmbedderInfo;
import net.yozmnews.evaluation.Embedding;

/**
 * 임베더 결과 확인 — 요청 수 · 밝힌 차원과 맞는지의 판정 · docs/specs/evaluation.md §4 · evaluation-R45.
 */
public final class EmbeddingCheck {

	private EmbeddingCheck() {
	}

	/**
	 * @param requested 넘긴 텍스트 수
	 * @return 어긋남의 문구 — 맞으면 빈 값. 개수가 다르거나, 차원이 {@code info.dimensions()} 와 다른 벡터가 있을 때
	 */
	public static Optional<String> mismatch(EmbedderInfo info, int requested, List<Embedding> embeddings) {
		Objects.requireNonNull(info, "info");
		Objects.requireNonNull(embeddings, "embeddings");
		if (embeddings.size() != requested) {
			return Optional.of("벡터 수가 텍스트 수와 다르다 — 텍스트 " + requested + "개, 벡터 " + embeddings.size() + "개");
		}
		for (int i = 0; i < embeddings.size(); i++) {
			int dimensions = embeddings.get(i).dimensions();
			if (dimensions != info.dimensions()) {
				return Optional.of(
						"벡터 차원이 임베더가 밝힌 차원과 다르다 — " + i + "번째 " + dimensions + ", 밝힌 차원 " + info.dimensions());
			}
		}
		return Optional.empty();
	}

}
