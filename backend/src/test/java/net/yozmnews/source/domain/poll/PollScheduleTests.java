package net.yozmnews.source.domain.poll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import net.yozmnews.source.FailurePermanence;
import net.yozmnews.source.domain.profile.PollInterval;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 수집 일정 산식 — 기대 시각은 손으로 센 값이다.
 * <p>
 * 지키는 규칙: source-R18 · source-R25
 */
class PollScheduleTests {

	private static final Instant AT = Instant.parse("2026-10-28T09:00:00Z");

	private static final PollInterval FIFTEEN_MINUTES = new PollInterval(Duration.ofMinutes(15));

	@ParameterizedTest
	@CsvSource({ "1, PT30M", "2, PT1H", "3, PT2H", "4, PT4H", "5, PT6H", "6, PT6H" })
	void temporaryFailuresDoubleUpToSixHours(int failures, Duration delay) {
		assertThat(PollSchedule.afterFailure(AT, FIFTEEN_MINUTES, failures, FailurePermanence.TEMPORARY))
			.isEqualTo(AT.plus(delay));
	}

	@ParameterizedTest
	@CsvSource({ "1, PT30M", "2, PT1H", "3, PT2H", "4, PT4H", "5, PT8H", "6, PT16H", "7, PT24H", "8, PT24H" })
	void permanentFailuresDoubleUpToADay(int failures, Duration delay) {
		assertThat(PollSchedule.afterFailure(AT, FIFTEEN_MINUTES, failures, FailurePermanence.PERMANENT))
			.isEqualTo(AT.plus(delay));
	}

	@Test
	void neverWaitsLessThanTheIntervalWhenItExceedsTheCap() {
		PollInterval twelveHours = new PollInterval(Duration.ofHours(12));

		assertThat(PollSchedule.afterFailure(AT, twelveHours, 1, FailurePermanence.TEMPORARY))
			.isEqualTo(Instant.parse("2026-10-28T21:00:00Z"));
	}

	@Test
	void manyFailuresStayAtTheCap() {
		assertThat(PollSchedule.afterFailure(AT, FIFTEEN_MINUTES, 64, FailurePermanence.TEMPORARY))
			.isEqualTo(Instant.parse("2026-10-28T15:00:00Z"));
		assertThat(PollSchedule.afterFailure(AT, FIFTEEN_MINUTES, Integer.MAX_VALUE, FailurePermanence.PERMANENT))
			.isEqualTo(Instant.parse("2026-10-29T09:00:00Z"));
	}

	@Test
	void rejectsFewerThanOneFailure() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> PollSchedule.afterFailure(AT, FIFTEEN_MINUTES, 0, FailurePermanence.TEMPORARY));
	}

	@Test
	void succeedsAfterOneInterval() {
		assertThat(PollSchedule.afterSuccess(AT, FIFTEEN_MINUTES)).isEqualTo(Instant.parse("2026-10-28T09:15:00Z"));
		assertThat(PollSchedule.afterSuccess(AT, new PollInterval(Duration.ofMinutes(30))))
			.isEqualTo(Instant.parse("2026-10-28T09:30:00Z"));
	}

	@Test
	void waitsUntilTheScheduledTime() {
		Lease lease = new Lease(UUID.randomUUID(), Instant.parse("2026-10-28T10:00:10Z"));

		assertThat(PollSchedule.waitBeforeFetch(lease, Instant.parse("2026-10-28T09:59:58Z")))
			.isEqualTo(Duration.ofSeconds(12));
		assertThat(PollSchedule.waitBeforeFetch(lease, Instant.parse("2026-10-28T10:00:10Z"))).isEqualTo(Duration.ZERO);
		assertThat(PollSchedule.waitBeforeFetch(lease, Instant.parse("2026-10-28T10:00:30Z"))).isEqualTo(Duration.ZERO);
	}

	@Test
	void reservesTheFirstRequestOfASourceNow() {
		Instant now = Instant.parse("2026-10-28T10:00:00Z");

		assertThat(PollSchedule.reserveFetch(null, now)).isEqualTo(new PollSchedule.FetchSlot(now,
				Instant.parse("2026-10-28T10:00:10Z")));
	}

	@Test
	void reservesTheNextRequestOfASourceAfterThePreviousOne() {
		Instant now = Instant.parse("2026-10-28T10:00:00Z");

		assertThat(PollSchedule.reserveFetch(Instant.parse("2026-10-28T10:00:04Z"), now))
			.isEqualTo(new PollSchedule.FetchSlot(Instant.parse("2026-10-28T10:00:04Z"),
					Instant.parse("2026-10-28T10:00:14Z")));
		assertThat(PollSchedule.reserveFetch(Instant.parse("2026-10-28T09:59:00Z"), now))
			.isEqualTo(new PollSchedule.FetchSlot(now, Instant.parse("2026-10-28T10:00:10Z")));
	}

	@Test
	void aReservationFitsTheLeaseOnlyWhenTheFetchCanEndBeforeItExpires() {
		PollSchedule.FetchSlot slot = new PollSchedule.FetchSlot(Instant.parse("2026-10-28T10:00:00Z"),
				Instant.parse("2026-10-28T10:00:10Z"));

		assertThat(PollSchedule.fitsLease(slot, Instant.parse("2026-10-28T10:00:11Z"))).isTrue();
		assertThat(PollSchedule.fitsLease(slot, Instant.parse("2026-10-28T10:00:10Z"))).isFalse();
	}

}
