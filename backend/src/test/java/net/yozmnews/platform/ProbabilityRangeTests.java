package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

/**
 * 확률 범위 — 0 이상 1 이하의 유한값만 들고(경계 포함), NaN · 무한대는 들지 않는다. 받지 않는 값의 문구는 값의 이름을 싣는다.
 */
class ProbabilityRangeTests {

	@Test
	void zeroOneAndValuesBetweenAreInTheRange() {
		assertThat(ProbabilityRange.contains(0.0)).isTrue();
		assertThat(ProbabilityRange.contains(1.0)).isTrue();
		assertThat(ProbabilityRange.contains(0.5)).isTrue();
		assertThat(ProbabilityRange.contains(Double.MIN_VALUE)).isTrue();
	}

	@Test
	void valuesOutsideZeroToOneNaNAndInfinitiesAreNot() {
		assertThat(ProbabilityRange.contains(-0.1)).isFalse();
		assertThat(ProbabilityRange.contains(1.0000001)).isFalse();
		assertThat(ProbabilityRange.contains(Double.NaN)).isFalse();
		assertThat(ProbabilityRange.contains(Double.POSITIVE_INFINITY)).isFalse();
		assertThat(ProbabilityRange.contains(Double.NEGATIVE_INFINITY)).isFalse();
	}

	@Test
	void requireReturnsAValueInTheRange() {
		assertThat(ProbabilityRange.require(0.0, "x.p")).isEqualTo(0.0);
		assertThat(ProbabilityRange.require(1.0, "x.p")).isEqualTo(1.0);
	}

	@Test
	void requireRefusesAValueOutsideTheRangeNamingIt() {
		assertThatIllegalArgumentException().isThrownBy(() -> ProbabilityRange.require(1.1, "x.p"))
			.withMessage("확률은 0 ~ 1 의 유한값이다 — x.p: 1.1");
		assertThatIllegalArgumentException().isThrownBy(() -> ProbabilityRange.require(-0.1, "x.p"))
			.withMessage("확률은 0 ~ 1 의 유한값이다 — x.p: -0.1");
		assertThatIllegalArgumentException().isThrownBy(() -> ProbabilityRange.require(Double.NaN, "x.p"))
			.withMessage("확률은 0 ~ 1 의 유한값이다 — x.p: NaN");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> ProbabilityRange.require(Double.POSITIVE_INFINITY, "x.p"))
			.withMessage("확률은 0 ~ 1 의 유한값이다 — x.p: Infinity");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> ProbabilityRange.require(Double.NEGATIVE_INFINITY, "x.p"))
			.withMessage("확률은 0 ~ 1 의 유한값이다 — x.p: -Infinity");
	}

	@Test
	void requireRefusesABlankOrMissingName() {
		assertThatIllegalArgumentException().isThrownBy(() -> ProbabilityRange.require(0.5, " "))
			.withMessage("값의 이름이 비었다");
		assertThatNullPointerException().isThrownBy(() -> ProbabilityRange.require(0.5, null));
	}

}
