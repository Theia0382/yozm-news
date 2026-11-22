package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * 관심 상수 묶음 — 주제 묶음의 값과 값의 범위 검사.
 */
class InterestConstantsTests {

	@Test
	void theTopicBundleHoldsTheTopicConstants() {
		assertThat(InterestRules.TOPIC).isEqualTo(new InterestConstants(Duration.ofDays(30), 3.0, 1.0, 0.5));
	}

	@Test
	void valuesOutsideTheirRangesAreRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new InterestConstants(Duration.ZERO, 3.0, 1.0, 0.5));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new InterestConstants(Duration.ofDays(-1), 3.0, 1.0, 0.5));
		assertThatIllegalArgumentException().isThrownBy(() -> new InterestConstants(Duration.ofDays(30), 0.0, 1.0, 0.5));
		assertThatIllegalArgumentException().isThrownBy(() -> new InterestConstants(Duration.ofDays(30), 3.0, 0.0, 0.5));
		assertThatIllegalArgumentException().isThrownBy(() -> new InterestConstants(Duration.ofDays(30), 3.0, 1.0, 0.0));
		assertThatIllegalArgumentException().isThrownBy(() -> new InterestConstants(Duration.ofDays(30), 3.0, 1.0, 1.0));
		// 대조 — 경계 안의 값은 받는다
		assertThat(new InterestConstants(Duration.ofMillis(1), 0.1, 0.1, 0.99).strength()).isEqualTo(0.99);
	}

}
