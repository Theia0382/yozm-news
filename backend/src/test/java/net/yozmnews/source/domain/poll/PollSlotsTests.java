package net.yozmnews.source.domain.poll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * 같은 소스의 예약 시각 분산 — 기대 시각은 손으로 센 값이다.
 * <p>
 * 지키는 규칙: source-R18
 */
class PollSlotsTests {

	private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

	@Test
	void spacesFeedsOfOneSourceFromNow() {
		assertThat(PollSlots.assign(NOW, null, 3)).containsExactly(Instant.parse("2026-10-28T10:00:00Z"),
				Instant.parse("2026-10-28T10:00:10Z"), Instant.parse("2026-10-28T10:00:20Z"));
	}

	@Test
	void startsAfterTheLastScheduledPollOfTheSource() {
		assertThat(PollSlots.assign(NOW, Instant.parse("2026-10-28T10:00:25Z"), 2))
			.containsExactly(Instant.parse("2026-10-28T10:00:35Z"), Instant.parse("2026-10-28T10:00:45Z"));
	}

	@Test
	void startsNowWhenTheLastScheduledPollIsLongPast() {
		assertThat(PollSlots.assign(NOW, Instant.parse("2026-10-28T09:59:00Z"), 1))
			.containsExactly(Instant.parse("2026-10-28T10:00:00Z"));
	}

	@Test
	void assignsNothingForNoFeeds() {
		assertThat(PollSlots.assign(NOW, null, 0)).isEmpty();
		assertThatIllegalArgumentException().isThrownBy(() -> PollSlots.assign(NOW, null, -1));
	}

}
