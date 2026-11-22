package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 구독 변경 신호 — 몫은 0 초과 1 이하, 부호는 +1 · −1 만 받는다.
 */
class SubscriptionSignalTests {

	private static final Instant T = Instant.parse("2026-11-17T00:00:00Z");

	@Test
	void sharesAboveZeroUpToOneAndASignOfOneAreAccepted() {
		assertThat(new SubscriptionSignal<>(Map.of("a", 1.0, "b", 0.25), -1, T).shares())
			.isEqualTo(Map.of("a", 1.0, "b", 0.25));
	}

	@Test
	void aShareOutsideZeroToOneIsRefused() {
		assertThatIllegalArgumentException().isThrownBy(() -> new SubscriptionSignal<>(Map.of("a", 0.0), 1, T));
		assertThatIllegalArgumentException().isThrownBy(() -> new SubscriptionSignal<>(Map.of("a", 1.5), 1, T));
	}

	@Test
	void aSignOtherThanPlusOrMinusOneIsRefused() {
		assertThatIllegalArgumentException().isThrownBy(() -> new SubscriptionSignal<>(Map.of("a", 1.0), 0, T));
		assertThatIllegalArgumentException().isThrownBy(() -> new SubscriptionSignal<>(Map.of("a", 1.0), 2, T));
	}

}
