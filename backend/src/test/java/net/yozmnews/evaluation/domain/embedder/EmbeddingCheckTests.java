package net.yozmnews.evaluation.domain.embedder;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.evaluation.EmbedderInfo;
import net.yozmnews.evaluation.Embedding;
import org.junit.jupiter.api.Test;

/**
 * 임베더 결과 확인 — 넘긴 수와 같고, 모든 벡터가 밝힌 차원인가.
 * <p>
 * 지키는 규칙: evaluation-R45
 */
class EmbeddingCheckTests {

	private static final EmbedderInfo INFO = new EmbedderInfo("test", "test-3", 3);

	private static final Embedding THREE = Embedding.of(new float[] { 1f, 0f, 0f });

	@Test
	void aResultMatchingTheCountAndDimensionsPasses() {
		assertThat(EmbeddingCheck.mismatch(INFO, 2, List.of(THREE, THREE))).isEmpty();
	}

	@Test
	void aResultWithAnotherCountIsAMismatch() {
		assertThat(EmbeddingCheck.mismatch(INFO, 2, List.of(THREE)))
			.hasValue("벡터 수가 텍스트 수와 다르다 — 텍스트 2개, 벡터 1개");
	}

	@Test
	void aVectorWithAnotherDimensionIsAMismatch() {
		Embedding two = Embedding.of(new float[] { 1f, 0f });

		assertThat(EmbeddingCheck.mismatch(INFO, 2, List.of(THREE, two)))
			.hasValue("벡터 차원이 임베더가 밝힌 차원과 다르다 — 1번째 2, 밝힌 차원 3");
	}

}
