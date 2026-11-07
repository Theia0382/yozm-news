package net.yozmnews.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/**
 * 사건 조회의 기간과 사건 ID — 기간은 시작이 끝보다 앞이고 날짜 31일(시각 32일)까지, 사건 ID 는 1 이상.
 * <p>
 * 지키는 규칙: event-R20
 */
class EventPeriodTests {

	private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");

	@Test
	void aTimeRangeIsAtMostThirtyTwoDays() {
		// 날짜 31일에 시간대 전환 여유로 하루를 더한다
		assertThat(EventPeriod.of(FROM, Instant.parse("2026-12-03T00:00:00Z"))).isPresent();
		assertThat(EventPeriod.of(FROM, Instant.parse("2026-12-03T00:00:00.000001Z"))).isEmpty();
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new EventPeriod(FROM, Instant.parse("2026-12-03T00:00:00.000001Z")));
	}

	@Test
	void thirtyOneDatesAcrossATransitionAreAPeriod() {
		// 2026-11-01 이 서머타임 해제일 — 시각 길이 31일 1시간
		assertThat(EventPeriod.ofDays(LocalDate.parse("2026-10-15"), LocalDate.parse("2026-11-14"),
				ZoneId.of("America/New_York")))
			.contains(new EventPeriod(Instant.parse("2026-10-15T04:00:00Z"), Instant.parse("2026-11-15T05:00:00Z")));
	}

	@Test
	void thirtyOneDatesWithoutATransitionAreThirtyOneDays() {
		assertThat(EventPeriod.ofDays(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-12-01"),
				ZoneId.of("Asia/Seoul")))
			.contains(new EventPeriod(Instant.parse("2026-10-31T15:00:00Z"), Instant.parse("2026-12-01T15:00:00Z")));
	}

	@Test
	void thirtyTwoDatesAreNoPeriod() {
		assertThat(EventPeriod.ofDays(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-12-02"),
				ZoneId.of("Asia/Seoul")))
			.isEmpty();
	}

	@Test
	void aPeriodStartsBeforeItEnds() {
		assertThat(EventPeriod.of(FROM, FROM)).isEmpty();
		assertThat(EventPeriod.of(FROM, FROM.minusSeconds(1))).isEmpty();
		assertThatIllegalArgumentException().isThrownBy(() -> new EventPeriod(FROM, FROM));
	}

	@Test
	void anEventIdIsPositive() {
		assertThat(new EventId(1).value()).isEqualTo(1);
		assertThatIllegalArgumentException().isThrownBy(() -> new EventId(0));
	}

}
