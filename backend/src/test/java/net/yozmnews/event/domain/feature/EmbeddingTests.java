package net.yozmnews.event.domain.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 벡터 값 — 길이 1 이상, 유한한 값, 복사해 받고 내기, 값 비교.
 * <p>
 * 지키는 규칙: event-R3
 */
class EmbeddingTests {

	@Test
	void anEmptyVectorIsRejected() {
		assertThatThrownBy(() -> Embedding.of(new float[0])).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void notANumberIsRejected() {
		assertThatThrownBy(() -> Embedding.of(new float[] { 1f, Float.NaN })).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void anInfiniteValueIsRejected() {
		assertThatThrownBy(() -> Embedding.of(new float[] { Float.NEGATIVE_INFINITY }))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void valuesAreCopiedInAndOut() {
		float[] source = { 0.25f, -0.5f };
		Embedding embedding = Embedding.of(source);
		source[0] = 9f;
		embedding.values()[1] = 9f;

		assertThat(embedding.values()).containsExactly(0.25f, -0.5f);
		assertThat(embedding.dimensions()).isEqualTo(2);
	}

	@Test
	void vectorsWithTheSameValuesAreEqual() {
		assertThat(Embedding.of(new float[] { 0.25f, -0.5f })).isEqualTo(Embedding.of(new float[] { 0.25f, -0.5f }))
			.hasSameHashCodeAs(Embedding.of(new float[] { 0.25f, -0.5f }))
			.isNotEqualTo(Embedding.of(new float[] { 0.25f, 0.5f }));
	}

}
