package net.yozmnews.storyline.domain.linking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * 잇기 기준 — 창은 0 보다 큰 초 단위, 기준은 0 보다 크고 1 이하.
 * <p>
 * 지키는 규칙: storyline-R9
 */
class LinkingCriteriaTests {

	@Test
	void acceptsAPositiveWholeSecondWindowAndAThresholdUpToOne() {
		assertThat(new LinkingCriteria(Duration.ofDays(14), 0.65).threshold()).isEqualTo(0.65);
		assertThat(new LinkingCriteria(Duration.ofSeconds(1), 1.0).window()).isEqualTo(Duration.ofSeconds(1));
	}

	@Test
	void rejectsAnInvalidWindowOrThreshold() {
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkingCriteria(Duration.ZERO, 0.65));
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkingCriteria(Duration.ofSeconds(-1), 0.65));
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkingCriteria(Duration.ofMillis(1500), 0.65));
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkingCriteria(Duration.ofDays(1), 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkingCriteria(Duration.ofDays(1), 1.1));
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkingCriteria(Duration.ofDays(1), Double.NaN));
	}

}
