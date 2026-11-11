package net.yozmnews.storyline.domain.linking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * 코사인 유사도 — 크기와 무관, 영 벡터는 NaN, 길이가 다르면 거부.
 * <p>
 * 지키는 규칙: storyline-R8
 */
class CosineTests {

	@Test
	void theCosineIgnoresLength() {
		assertThat(Cosine.of(new float[] { 1, 0 }, new float[] { 4, 3 })).isCloseTo(0.8, within(1e-12));
		assertThat(Cosine.of(new float[] { 2, 0 }, new float[] { 4, 3 })).isCloseTo(0.8, within(1e-12));
		assertThat(Cosine.of(new float[] { 1, 0 }, new float[] { -1, 0 })).isCloseTo(-1, within(1e-12));
	}

	@Test
	void aZeroVectorHasNoCosine() {
		assertThat(Cosine.of(new float[] { 0, 0 }, new float[] { 1, 0 })).isNaN();
	}

	@Test
	void differentLengthsAreRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> Cosine.of(new float[] { 1 }, new float[] { 1, 0 }));
	}

}
