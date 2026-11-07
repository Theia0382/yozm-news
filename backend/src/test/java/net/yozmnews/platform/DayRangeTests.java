package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/**
 * 날짜 범위 → 시각 범위 — 시간대의 자정, 날짜 수 상한, 시간대 전환 여유의 하루, 시작이 끝보다 앞.
 */
class DayRangeTests {

	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Test
	void aRangeRunsFromTheFirstMidnightToTheMidnightAfterTheLastDay() {
		// 2026-11-01 이 서머타임 해제일 — 31일 범위의 시각 길이는 31일 1시간(745시간)이다
		DayRange range = DayRange.of(LocalDate.parse("2026-10-15"), LocalDate.parse("2026-11-14"), NEW_YORK, 31)
			.orElseThrow();

		assertThat(range).isEqualTo(
				new DayRange(Instant.parse("2026-10-15T04:00:00Z"), Instant.parse("2026-11-15T05:00:00Z")));
		assertThat(Duration.between(range.from(), range.to())).isEqualTo(Duration.ofHours(745));
	}

	@Test
	void aZoneWithoutTransitionsGivesWholeDays() {
		DayRange range = DayRange.of(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-12-01"), SEOUL, 31)
			.orElseThrow();

		assertThat(range).isEqualTo(
				new DayRange(Instant.parse("2026-10-31T15:00:00Z"), Instant.parse("2026-12-01T15:00:00Z")));
		assertThat(Duration.between(range.from(), range.to())).isEqualTo(Duration.ofDays(31));
	}

	@Test
	void theDayCountStaysWithinOneToTheMaximum() {
		assertThat(DayRange.of(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-01"), SEOUL, 31)).isPresent();
		assertThat(DayRange.of(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-12-02"), SEOUL, 31)).isEmpty();
		assertThat(DayRange.of(LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-01"), SEOUL, 31)).isEmpty();
		assertThat(DayRange.of(LocalDate.MAX.minusDays(1), LocalDate.MAX, SEOUL, 31)).isEmpty();
	}

	@Test
	void theLengthLimitAddsADayForTransitions() {
		assertThat(DayRange.maxLength(31)).isEqualTo(Duration.ofDays(32));
		assertThat(DayRange.maxLength(1)).isEqualTo(Duration.ofDays(2));
		assertThatIllegalArgumentException().isThrownBy(() -> DayRange.maxLength(0));
	}

	@Test
	void aRangeStartsBeforeItEnds() {
		Instant at = Instant.parse("2026-11-01T00:00:00Z");
		assertThatIllegalArgumentException().isThrownBy(() -> new DayRange(at, at));
	}

}
