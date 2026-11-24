package net.yozmnews.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.Map;

import net.yozmnews.account.domain.guard.ClientAddress;
import net.yozmnews.account.domain.guard.LimitDecision;
import net.yozmnews.account.domain.guard.LimitKey;
import net.yozmnews.account.domain.guard.LimitWindow;
import net.yozmnews.account.domain.guard.RequestCounts;
import net.yozmnews.platform.GuardedAction;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.Test;

/**
 * 요청 제한 유스케이스 — 동작의 규칙마다 세고, 한도를 넘은 요청은 그 창 끝까지 넘었다. 키 등록 · 검증은 사용자와 주소로 따로 센다. 저장소의
 * 셈은 메모리 대역이다(DB 의 셈은 {@code JdbcRequestCountsTests}).
 * <p>
 * 지키는 규칙: account-R30 · account-R31
 */
class RequestLimiterTests {

	private static final Instant NOW = Instant.parse("2026-11-14T10:03:00Z");

	private final MemoryCounts counts = new MemoryCounts();

	private final RequestLimiter limiter = new RequestLimiter(this.counts, InstantSource.fixed(NOW));

	@Test
	void thirtySignInsFromOneAddressPassAndTheThirtyFirstWaitsForTheTenMinuteWindow() {
		ClientAddress address = address("192.0.2.1");
		for (int i = 1; i <= 30; i++) {
			assertThat(this.limiter.consume(GuardedAction.SIGN_IN, address, null)).as("요청 %d", i)
				.isEqualTo(new LimitDecision.Allowed());
		}

		assertThat(this.limiter.consume(GuardedAction.SIGN_IN, address, null))
			.isEqualTo(new LimitDecision.Exceeded(Instant.parse("2026-11-14T10:10:00Z")));
	}

	@Test
	void anotherAddressHasItsOwnCount() {
		for (int i = 1; i <= 31; i++) {
			this.limiter.consume(GuardedAction.SIGN_IN, address("192.0.2.1"), null);
		}

		assertThat(this.limiter.consume(GuardedAction.SIGN_IN, address("192.0.2.2"), null))
			.isEqualTo(new LimitDecision.Allowed());
	}

	@Test
	void llmKeyChecksStopAtTheTwentyFirstForOneUserFromManyAddresses() {
		UserId user = new UserId(7);
		for (int i = 1; i <= 20; i++) {
			assertThat(this.limiter.consume(GuardedAction.LLM_KEY_CHECK, address("198.51.100." + i), user))
				.as("요청 %d", i)
				.isEqualTo(new LimitDecision.Allowed());
		}

		assertThat(this.limiter.consume(GuardedAction.LLM_KEY_CHECK, address("198.51.100.21"), user))
			.isEqualTo(new LimitDecision.Exceeded(Instant.parse("2026-11-14T11:00:00Z")));
	}

	@Test
	void llmKeyChecksStopAtTheSixtyFirstFromOneAddressForManyUsers() {
		ClientAddress address = address("203.0.113.9");
		for (int i = 1; i <= 60; i++) {
			assertThat(this.limiter.consume(GuardedAction.LLM_KEY_CHECK, address, new UserId(100 + i)))
				.as("요청 %d", i)
				.isEqualTo(new LimitDecision.Allowed());
		}

		assertThat(this.limiter.consume(GuardedAction.LLM_KEY_CHECK, address, new UserId(161)))
			.isEqualTo(new LimitDecision.Exceeded(Instant.parse("2026-11-14T11:00:00Z")));
	}

	@Test
	void retryAfterCountsTheSecondsLeftInTheWindow() {
		assertThat(this.limiter.retryAfterSeconds(new LimitDecision.Exceeded(Instant.parse("2026-11-14T10:10:00Z"))))
			.isEqualTo(420);
	}

	@Test
	void aUserRuleWithoutASessionUserIsAMisuseOfTheMark() {
		assertThatIllegalStateException()
			.isThrownBy(() -> this.limiter.consume(GuardedAction.LLM_KEY_CHECK, address("192.0.2.1"), null));
	}

	private static ClientAddress address(String literal) {
		return ClientAddress.parse(literal).orElseThrow();
	}

	/** 키 · 창 시작마다 더하는 메모리 저장소 */
	private static final class MemoryCounts implements RequestCounts {

		private final Map<String, Integer> rows = new HashMap<>();

		@Override
		public int increment(LimitKey key, LimitWindow window) {
			return this.rows.merge(key + "@" + window.start(), 1, Integer::sum);
		}

		@Override
		public int forgetEndedBefore(Instant cutoff) {
			throw new UnsupportedOperationException();
		}

	}

}
