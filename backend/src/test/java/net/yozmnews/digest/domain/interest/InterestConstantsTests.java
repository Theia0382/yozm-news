package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * 관심 상수 묶음 — 주제 · 스토리라인 묶음의 값과 값의 범위 검사.
 */
class InterestConstantsTests {

	@Test
	void theTopicBundleHoldsTheTopicConstants() {
		assertThat(InterestRules.TOPIC).isEqualTo(new InterestConstants(Duration.ofDays(30), 3.0, 0.1, 0.5, false));
	}

	@Test
	void theStorylineBundleHoldsTheStorylineConstants() {
		// 주제와 γ · 켜짐이 다르다 — 추적만 한 흐름의 관심 1/3, 반응이 앞선 무반응 노출을 대신한다
		assertThat(InterestRules.STORYLINE).isEqualTo(new InterestConstants(Duration.ofDays(30), 3.0, 1.0 / 3.0, 0.5, true));
	}

	@Test
	void valuesOutsideTheirRangesAreRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new InterestConstants(Duration.ZERO, 3.0, 1.0, 0.5, false));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new InterestConstants(Duration.ofDays(-1), 3.0, 1.0, 0.5, false));
		assertThatIllegalArgumentException().isThrownBy(() -> new InterestConstants(Duration.ofDays(30), 0.0, 1.0, 0.5, false));
		assertThatIllegalArgumentException().isThrownBy(() -> new InterestConstants(Duration.ofDays(30), 3.0, 0.0, 0.5, false));
		assertThatIllegalArgumentException().isThrownBy(() -> new InterestConstants(Duration.ofDays(30), 3.0, 1.0, 0.0, false));
		assertThatIllegalArgumentException().isThrownBy(() -> new InterestConstants(Duration.ofDays(30), 3.0, 1.0, 1.0, false));
		// 대조 — 경계 안의 값은 받는다
		assertThat(new InterestConstants(Duration.ofMillis(1), 0.1, 0.1, 0.99, false).strength()).isEqualTo(0.99);
	}

}
