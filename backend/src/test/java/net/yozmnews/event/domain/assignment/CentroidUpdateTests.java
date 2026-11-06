package net.yozmnews.event.domain.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * 중심의 누적 갱신 — 더하면 평균, 빼면 그 역. 반 정밀도로 저장하며 300번 더해도 평균에서 크게 벗어나지 않는다 — 매번 결과를
 * {@code Float.floatToFloat16} · {@code Float.float16ToFloat} 로 반 정밀도에 맞춰 {@code halfvec} 의 저장 · 읽기를 흉내 낸다.
 * <p>
 * 지키는 규칙: event-R12
 */
class CentroidUpdateTests {

	@Test
	void addingAVectorAveragesIt() {
		assertThat(CentroidUpdate.add(new float[] { 1, 0 }, 1, new float[] { 0, 1 })).containsExactly(0.5f, 0.5f);
		// (0.5 × 2 + 1) / 3 = 2/3, (0.5 × 2 + 0) / 3 = 1/3
		assertThat(CentroidUpdate.add(new float[] { 0.5f, 0.5f }, 2, new float[] { 1, 0 }))
			.containsExactly(0.6666667f, 0.33333334f);
	}

	@Test
	void removingAVectorUndoesTheAddition() {
		assertThat(CentroidUpdate.remove(new float[] { 0.5f, 0.5f }, 2, new float[] { 0, 1 })).containsExactly(1f, 0f);
	}

	@Test
	void threeHundredHalfPrecisionAdditionsStayNearTheMean() {
		float[][] basis = { { 1, 0, 0 }, { 0, 1, 0 }, { 0, 0, 1 } };
		float[] centroid = basis[0];
		for (int count = 1; count < 300; count++) {
			centroid = halfPrecision(CentroidUpdate.add(centroid, count, basis[count % 3]));
		}
		// 세 기저 벡터를 100번씩 더한 평균은 1/3 이다. 허용 오차는 반 정밀도 반올림(상대 2^−11 ≈ 4.9e-4)이 300번 무작위로
		// 쌓인 크기 4.9e-4 × √(300/3) ≈ 4.9e-3 이다
		for (float component : centroid) {
			assertThat((double) component).isCloseTo(0.3333333333333333, within(4.9e-3));
		}
	}

	@Test
	void countsAndLengthsAreChecked() {
		assertThatIllegalArgumentException().isThrownBy(() -> CentroidUpdate.add(new float[] { 1 }, 0, new float[] { 1 }));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> CentroidUpdate.remove(new float[] { 1 }, 1, new float[] { 1 }));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> CentroidUpdate.add(new float[] { 1, 0 }, 1, new float[] { 1 }));
	}

	private static float[] halfPrecision(float[] vector) {
		float[] rounded = new float[vector.length];
		for (int i = 0; i < vector.length; i++) {
			rounded[i] = Float.float16ToFloat(Float.floatToFloat16(vector[i]));
		}
		return rounded;
	}

}
