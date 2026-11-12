package net.yozmnews.user.domain.throttle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * 로그인 실패 지연 일정 — 3회까지 없음, 4회째부터 1초에서 두 배씩, 상한 15분, 마지막 실패에서 24시간이 지나면 잊는다.
 * <p>
 * 지키는 규칙: user-R19
 */
class LoginThrottleTests {

	private static final Instant LAST = Instant.parse("2026-11-13T09:00:00Z");

	@Test
	void firstThreeFailuresDoNotWait() {
		assertThat(LoginThrottle.retryAt(new FailureRecord(1, LAST), LAST)).isEmpty();
		assertThat(LoginThrottle.retryAt(new FailureRecord(3, LAST), LAST)).isEmpty();
	}

	@Test
	void waitDoublesFromOneSecondAndStopsAtFifteenMinutes() {
		assertThat(LoginThrottle.retryAt(new FailureRecord(4, LAST), LAST)).contains(LAST.plusSeconds(1));
		assertThat(LoginThrottle.retryAt(new FailureRecord(5, LAST), LAST)).contains(LAST.plusSeconds(2));
		assertThat(LoginThrottle.retryAt(new FailureRecord(6, LAST), LAST)).contains(LAST.plusSeconds(4));
		assertThat(LoginThrottle.retryAt(new FailureRecord(13, LAST), LAST)).contains(LAST.plusSeconds(512));
		// 14회째의 1024초는 상한 900초에서 잘린다
		assertThat(LoginThrottle.retryAt(new FailureRecord(14, LAST), LAST)).contains(LAST.plusSeconds(900));
		assertThat(LoginThrottle.retryAt(new FailureRecord(200, LAST), LAST)).contains(LAST.plusSeconds(900));
	}

	@Test
	void waitEndsAtTheRetryTime() {
		FailureRecord fifth = new FailureRecord(5, LAST);

		assertThat(LoginThrottle.retryAt(fifth, LAST.plusMillis(1_999))).contains(LAST.plusSeconds(2));
		assertThat(LoginThrottle.retryAt(fifth, LAST.plusSeconds(2))).isEmpty();
	}

	@Test
	void failuresAreForgottenAfterADay() {
		FailureRecord many = new FailureRecord(20, LAST);

		assertThat(LoginThrottle.retryAt(many, LAST.plusSeconds(86_399))).as("상한 15분은 이미 지났다").isEmpty();
		assertThat(LoginThrottle.failed(many, LAST.plusSeconds(86_399))).isEqualTo(new FailureRecord(21,
				LAST.plusSeconds(86_399)));
		assertThat(LoginThrottle.failed(many, LAST.plusSeconds(86_400))).isEqualTo(new FailureRecord(1,
				LAST.plusSeconds(86_400)));
		assertThat(LoginThrottle.forgetBefore(LAST)).isEqualTo(Instant.parse("2026-11-12T09:00:00Z"));
	}

	@Test
	void firstFailureStartsTheRecord() {
		assertThat(LoginThrottle.failed(FailureRecord.NONE, LAST)).isEqualTo(new FailureRecord(1, LAST));
		assertThat(LoginThrottle.retryAt(FailureRecord.NONE, LAST)).isEmpty();
	}

	@Test
	void recordKeepsCountAndTimeTogether() {
		assertThatIllegalArgumentException().isThrownBy(() -> new FailureRecord(0, LAST));
		assertThatIllegalArgumentException().isThrownBy(() -> new FailureRecord(1, null));
		assertThatIllegalArgumentException().isThrownBy(() -> new FailureRecord(-1, null));
	}

}
