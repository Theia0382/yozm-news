package net.yozmnews.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * 대기의 남은 초 — 올림이고 1 이상이다.
 * <p>
 * 지키는 규칙: user-R17
 */
class SignInResponseTests {

	private static final Instant NOW = Instant.parse("2026-11-13T09:00:00Z");

	@Test
	void remainingSecondsAreRoundedUp() {
		assertThat(SignInResponse.waiting(NOW.plusSeconds(64), NOW).retryAfterSeconds()).isEqualTo(64L);
		assertThat(SignInResponse.waiting(NOW.plusMillis(63_001), NOW).retryAfterSeconds()).isEqualTo(64L);
		assertThat(SignInResponse.waiting(NOW.plusMillis(63_999), NOW).retryAfterSeconds()).isEqualTo(64L);
		assertThat(SignInResponse.waiting(NOW.plusMillis(200), NOW).retryAfterSeconds()).isEqualTo(1L);
	}

	@Test
	void otherResultsCarryNoSeconds() {
		assertThat(SignInResponse.signedIn().retryAfterSeconds()).isNull();
		assertThat(SignInResponse.rejected().retryAfterSeconds()).isNull();
	}

}
