package net.yozmnews.storyline.domain.linking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * 잇기 기준 — 창은 0 보다 큰 초 단위, 0 &lt; 아래 기준 ≤ 위 기준 ≤ 1, 확률 기준은 0 보다 크고 1 이하. 아래 기준이 위 기준보다 작을 때만 띠가 있다.
 * <p>
 * 지키는 규칙: storyline-R9 · storyline-R35
 */
class LinkingCriteriaTests {

	@Test
	void acceptsAPositiveWholeSecondWindowAndOrderedThresholdsUpToOne() {
		LinkingCriteria criteria = new LinkingCriteria(Duration.ofDays(14), 0.84, 0.90, 0.50);
		assertThat(criteria.lower()).isEqualTo(0.84);
		assertThat(criteria.upper()).isEqualTo(0.90);
		assertThat(criteria.probability()).isEqualTo(0.50);
		assertThat(new LinkingCriteria(Duration.ofSeconds(1), 1.0, 1.0, 1.0).window()).isEqualTo(Duration.ofSeconds(1));
	}

	@Test
	void theBandIsFromTheLowerThresholdUpToButExcludingTheUpper() {
		LinkingCriteria banded = new LinkingCriteria(Duration.ofDays(14), 0.84, 0.90, 0.50);
		assertThat(banded.inBand(score(0.84))).isTrue();
		assertThat(banded.inBand(score(0.89))).isTrue();
		assertThat(banded.inBand(score(0.83))).isFalse();
		assertThat(banded.inBand(score(0.90))).isFalse();
		assertThat(banded.inBand(score(Double.NaN))).isFalse();
		// 아래 기준이 위 기준과 같으면 띠가 비었다
		assertThat(new LinkingCriteria(Duration.ofDays(14), 0.86, 0.86, 0.50).inBand(score(0.86))).isFalse();
	}

	@Test
	void rejectsAnInvalidWindow() {
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkingCriteria(Duration.ZERO, 0.65, 0.65, 0.5));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new LinkingCriteria(Duration.ofSeconds(-1), 0.65, 0.65, 0.5));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new LinkingCriteria(Duration.ofMillis(1500), 0.65, 0.65, 0.5));
	}

	@Test
	void rejectsThresholdsOutOfOrderOrRange() {
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkingCriteria(Duration.ofDays(1), 0, 0.65, 0.5));
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkingCriteria(Duration.ofDays(1), 0.9, 0.84, 0.5));
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkingCriteria(Duration.ofDays(1), 0.84, 1.1, 0.5));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new LinkingCriteria(Duration.ofDays(1), Double.NaN, 0.86, 0.5));
	}

	@Test
	void rejectsAProbabilityThresholdOutOfRange() {
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkingCriteria(Duration.ofDays(1), 0.84, 0.9, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkingCriteria(Duration.ofDays(1), 0.84, 0.9, 1.1));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new LinkingCriteria(Duration.ofDays(1), 0.84, 0.9, Double.NaN));
	}

	private static Score score(double score) {
		return new Score(score, 0.9, 0.5);
	}

}
