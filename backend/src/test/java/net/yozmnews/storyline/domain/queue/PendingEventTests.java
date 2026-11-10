package net.yozmnews.storyline.domain.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * 잇기 대기 행 — 점유는 지금 + 10분까지, 다시 알림 수 · 실패 수는 그대로. 실패 상한은 3.
 * <p>
 * 지키는 규칙: storyline-R3 · storyline-R4
 */
class PendingEventTests {

	@Test
	void claimingLeasesForTenMinutesAndKeepsTheCounts() {
		UUID token = UUID.fromString("00000000-0000-0000-0000-000000000001");
		PendingEvent pending = new PendingEvent(5, Instant.parse("2026-11-01T00:00:00Z"), 2, 1, null);

		PendingEvent claimed = pending.claim(token, Instant.parse("2026-11-10T00:00:00Z"));

		assertThat(claimed.lease()).isEqualTo(new LinkingLease(token, Instant.parse("2026-11-10T00:10:00Z")));
		assertThat(claimed.notifications()).isEqualTo(2);
		assertThat(claimed.failures()).isEqualTo(1);
		assertThat(claimed.orderAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
	}

	@Test
	void negativeCountsAreRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new PendingEvent(5, null, -1, 0, null));
		assertThatIllegalArgumentException().isThrownBy(() -> new PendingEvent(5, null, 0, -1, null));
	}

	@Test
	void failuresAreExhaustedAtThree() {
		assertThat(LinkingSchedule.failuresExhausted(2)).isFalse();
		assertThat(LinkingSchedule.failuresExhausted(3)).isTrue();
	}

}
