package net.yozmnews.llm.domain.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import net.yozmnews.llm.LlmFailureKind;
import org.junit.jupiter.api.Test;

/**
 * 생성 호출을 다시 보낼지 — 일시 장애만, 최대 네 번 보내고, 간격은 2 · 4 · 8초이거나 공급자가 준 시간(60초까지)이다. 마감 초과는 다시
 * 보내지 않는다.
 * <p>
 * 지키는 규칙: llm-R20
 */
class RetryPolicyTests {

	@Test
	void backsOffTwoFourEightSecondsAndStopsAfterFourSends() {
		assertThat(RetryPolicy.next(1, LlmFailureKind.TRANSIENT, null, false)).contains(Duration.ofSeconds(2));
		assertThat(RetryPolicy.next(2, LlmFailureKind.TRANSIENT, null, false)).contains(Duration.ofSeconds(4));
		assertThat(RetryPolicy.next(3, LlmFailureKind.TRANSIENT, null, false)).contains(Duration.ofSeconds(8));
		assertThat(RetryPolicy.next(4, LlmFailureKind.TRANSIENT, null, false)).isEmpty();
	}

	@Test
	void followsTheProvidersWaitUpToSixtySeconds() {
		assertThat(RetryPolicy.next(1, LlmFailureKind.TRANSIENT, Duration.ofSeconds(30), false))
			.contains(Duration.ofSeconds(30));
		assertThat(RetryPolicy.next(3, LlmFailureKind.TRANSIENT, Duration.ofSeconds(60), false))
			.contains(Duration.ofSeconds(60));
		assertThat(RetryPolicy.next(1, LlmFailureKind.TRANSIENT, Duration.ofSeconds(0), false))
			.contains(Duration.ZERO);
		assertThat(RetryPolicy.next(1, LlmFailureKind.TRANSIENT, Duration.ofSeconds(61), false)).isEmpty();
		assertThat(RetryPolicy.next(4, LlmFailureKind.TRANSIENT, Duration.ofSeconds(1), false)).isEmpty();
	}

	@Test
	void doesNotResendAfterTheDeadlinePassed() {
		assertThat(RetryPolicy.next(1, LlmFailureKind.TRANSIENT, null, true)).isEmpty();
		assertThat(RetryPolicy.next(1, LlmFailureKind.TRANSIENT, Duration.ofSeconds(5), true)).isEmpty();
	}

	@Test
	void doesNotResendOtherKinds() {
		assertThat(RetryPolicy.next(1, LlmFailureKind.INVALID_KEY, null, false)).isEmpty();
		assertThat(RetryPolicy.next(1, LlmFailureKind.PERMISSION_DENIED, null, false)).isEmpty();
		assertThat(RetryPolicy.next(1, LlmFailureKind.QUOTA_EXHAUSTED, Duration.ofSeconds(5), false)).isEmpty();
		assertThat(RetryPolicy.next(1, LlmFailureKind.UNEXPECTED, null, false)).isEmpty();
	}

	@Test
	void refusesACountBelowOne() {
		assertThatThrownBy(() -> RetryPolicy.next(0, LlmFailureKind.TRANSIENT, null, false))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
