package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 벡터의 pgvector 텍스트 표현 — 쓰기와 되읽기, 빈 배열 · 유한하지 않은 값 · 꼴이 아닌 텍스트 거부.
 */
class VectorTextTests {

	@Test
	void writesBracketedCommaSeparatedFloats() {
		assertThat(VectorText.of(new float[] { 1.0f, -0.5f })).isEqualTo("[1.0,-0.5]");
		assertThat(VectorText.of(new float[] { 1.0E-5f })).isEqualTo("[1.0E-5]");
	}

	@Test
	void parsesWhatItWrites() {
		assertThat(VectorText.parse("[1.0,-0.5]")).containsExactly(1.0f, -0.5f);
		assertThat(VectorText.parse("[0.25]")).containsExactly(0.25f);
	}

	@Test
	void rejectsAnEmptyOrNonFiniteVector() {
		assertThatThrownBy(() -> VectorText.of(new float[0])).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> VectorText.of(new float[] { 1.0f, Float.NaN }))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> VectorText.of(new float[] { Float.POSITIVE_INFINITY }))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsTextThatIsNotAVector() {
		assertThatThrownBy(() -> VectorText.parse("1,2")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> VectorText.parse("[]")).isInstanceOf(IllegalArgumentException.class);
	}

}
