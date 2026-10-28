package net.yozmnews.source.domain.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * 한 번의 수집에 거는 제한 값.
 */
class FetchLimitsTests {

	@Test
	void defaultsAreTenSecondsFiveMebibytesFiveRedirects() {
		assertThat(FetchLimits.DEFAULT.deadline()).isEqualTo(Duration.ofSeconds(10));
		assertThat(FetchLimits.DEFAULT.maxBodyBytes()).isEqualTo(5_242_880L);
		assertThat(FetchLimits.DEFAULT.maxRedirects()).isEqualTo(5);
	}

	@Test
	void rejectsNonPositiveLimits() {
		assertThatIllegalArgumentException().isThrownBy(() -> new FetchLimits(Duration.ZERO, 1, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new FetchLimits(Duration.ofSeconds(-1), 1, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new FetchLimits(Duration.ofSeconds(1), 0, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new FetchLimits(Duration.ofSeconds(1), 1, -1));
	}

	@Test
	void zeroRedirectsIsAllowed() {
		assertThat(new FetchLimits(Duration.ofMillis(1), 1, 0).maxRedirects()).isZero();
	}

}
