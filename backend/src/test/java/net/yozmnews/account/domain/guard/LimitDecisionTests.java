package net.yozmnews.account.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 요청 제한 판정 — 이 요청까지 센 수가 한도보다 크면 창 끝까지 넘었다. 여러 규칙이면 가장 늦게 풀리는 것.
 * <p>
 * 지키는 규칙: user-R39
 */
class LimitDecisionTests {

	private static final LimitRule THIRTY = new LimitRule(LimitAxis.CLIENT_ADDRESS, 30, Duration.parse("PT10M"));

	private static final LimitWindow WINDOW = new LimitWindow(Instant.parse("2026-11-14T10:00:00Z"),
			Instant.parse("2026-11-14T10:10:00Z"));

	@Test
	void theLimitItselfIsStillAllowed() {
		assertThat(LimitDecision.of(30, THIRTY, WINDOW)).isEqualTo(new LimitDecision.Allowed());
	}

	@Test
	void oneOverTheLimitWaitsForTheEndOfTheWindow() {
		assertThat(LimitDecision.of(31, THIRTY, WINDOW))
			.isEqualTo(new LimitDecision.Exceeded(Instant.parse("2026-11-14T10:10:00Z")));
	}

	@Test
	void strictestTakesTheLatestExceededAndIgnoresAllowed() {
		assertThat(LimitDecision.strictest(List.of(new LimitDecision.Exceeded(Instant.parse("2026-11-14T10:10:00Z")),
				new LimitDecision.Allowed(), new LimitDecision.Exceeded(Instant.parse("2026-11-14T11:00:00Z")))))
			.isEqualTo(new LimitDecision.Exceeded(Instant.parse("2026-11-14T11:00:00Z")));
	}

	@Test
	void strictestOfAllowedIsAllowed() {
		assertThat(LimitDecision.strictest(List.of(new LimitDecision.Allowed(), new LimitDecision.Allowed())))
			.isEqualTo(new LimitDecision.Allowed());
	}

}
