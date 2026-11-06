package net.yozmnews.event.domain.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * 일시 실패 뒤의 재시도 시각 — 1분부터 두 배씩, 1시간에서 멈춘다.
 */
class AssignmentScheduleTests {

	private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

	@Test
	void theDelayDoublesFromOneMinuteAndStopsAtAnHour() {
		assertThat(AssignmentSchedule.retryAt(NOW, 1)).isEqualTo(Instant.parse("2026-11-06T00:01:00Z"));
		assertThat(AssignmentSchedule.retryAt(NOW, 2)).isEqualTo(Instant.parse("2026-11-06T00:02:00Z"));
		assertThat(AssignmentSchedule.retryAt(NOW, 4)).isEqualTo(Instant.parse("2026-11-06T00:08:00Z"));
		assertThat(AssignmentSchedule.retryAt(NOW, 6)).isEqualTo(Instant.parse("2026-11-06T00:32:00Z"));
		assertThat(AssignmentSchedule.retryAt(NOW, 7)).isEqualTo(Instant.parse("2026-11-06T01:00:00Z"));
		assertThat(AssignmentSchedule.retryAt(NOW, 1_000)).isEqualTo(Instant.parse("2026-11-06T01:00:00Z"));
	}

	@Test
	void attemptsStartAtOne() {
		assertThatIllegalArgumentException().isThrownBy(() -> AssignmentSchedule.retryAt(NOW, 0));
	}

}
