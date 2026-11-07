package net.yozmnews.event.domain.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 임베딩 결과 — 성공은 벡터를 복사해 받고, 예상한 실패는 세 갈래와 비어 있지 않은 문구를 갖는다.
 * <p>
 * 지키는 규칙: event-R6
 */
class EmbeddingResultTests {

	@Test
	void hasThreeFailureKindsInDeclaredOrder() {
		assertThat(EmbedderFailureKind.values()).containsExactly(EmbedderFailureKind.TRANSIENT,
				EmbedderFailureKind.INPUT_REJECTED, EmbedderFailureKind.EMBEDDER_UNAVAILABLE);
	}

	@Test
	void aFailureRefusesABlankMessage() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new EmbeddingFailure(EmbedderFailureKind.INPUT_REJECTED, " "));
		assertThat(new EmbeddingFailure(EmbedderFailureKind.INPUT_REJECTED, "입력이 거부됐다").message())
			.isEqualTo("입력이 거부됐다");
	}

	@Test
	void embeddingsAreCopiedInAndRefuseAMissingVector() {
		Embedding one = Embedding.of(new float[] { 1f, 0f });
		List<Embedding> source = new ArrayList<>(List.of(one));
		Embeddings embeddings = new Embeddings(source);
		source.add(Embedding.of(new float[] { 0f, 1f }));

		assertThat(embeddings.vectors()).containsExactly(one);
		assertThatNullPointerException().isThrownBy(() -> new Embeddings(Arrays.asList(one, null)));
	}

}
