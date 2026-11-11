package net.yozmnews.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/**
 * 집계 기간 — 시각 범위의 규칙(시작 &lt; 끝 · 32일 이하)과 날짜 범위의 시간대 자정 변환(1 ~ 31일)을 본다.
 * <p>
 * 시간대 전환 날짜는 JDK tzdb 의 값이다 — 시험이 먼저 그 사실을 확인하고 나서 기간을 단언한다.
 * <p>
 * 지키는 규칙: tag-R28
 */
class EvaluationPeriodTests {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Test
	void aDateRangeRunsFromTheFirstMidnightToTheMidnightAfterTheLastDayInThatZone() {
		EvaluationPeriod period = EvaluationPeriod.ofDays(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 3), SEOUL)
			.orElseThrow();

		assertThat(period.from()).isEqualTo(Instant.parse("2026-10-27T15:00:00Z"));
		assertThat(period.to()).isEqualTo(Instant.parse("2026-11-03T15:00:00Z"));
	}

	@Test
	void aDateRangeHasOneToThirtyOneDays() {
		assertThat(EvaluationPeriod.ofDays(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3), SEOUL)).hasValue(
				new EvaluationPeriod(Instant.parse("2026-11-02T15:00:00Z"), Instant.parse("2026-11-03T15:00:00Z")));
		assertThat(EvaluationPeriod.ofDays(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), SEOUL)).isPresent();
		assertThat(EvaluationPeriod.ofDays(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), SEOUL)).isEmpty();
		assertThat(EvaluationPeriod.ofDays(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 2), SEOUL)).isEmpty();
	}

	@Test
	void thirtyOneDaysWithATwentyFiveHourDayStayWithinTheLimit() {
		ZoneId london = ZoneId.of("Europe/London");
		// 2026-10-25 는 영국 서머타임이 끝나 25시간이다
		assertThat(Duration.between(LocalDate.of(2026, 10, 25).atStartOfDay(london),
				LocalDate.of(2026, 10, 26).atStartOfDay(london))).isEqualTo(Duration.ofHours(25));

		EvaluationPeriod period = EvaluationPeriod.ofDays(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), london)
			.orElseThrow();

		assertThat(period.from()).isEqualTo(Instant.parse("2026-09-30T23:00:00Z"));
		assertThat(period.to()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
	}

	@Test
	void aDayWithoutMidnightStartsAtItsFirstValidTime() {
		ZoneId saoPaulo = ZoneId.of("America/Sao_Paulo");
		// 2018-11-04 는 브라질 서머타임이 시작해 자정이 없다 — 하루가 01:00 에 시작한다
		assertThat(LocalDate.of(2018, 11, 4).atStartOfDay(saoPaulo).toLocalTime()).isEqualTo(LocalTime.of(1, 0));

		EvaluationPeriod period = EvaluationPeriod.ofDays(LocalDate.of(2018, 11, 4), LocalDate.of(2018, 11, 4), saoPaulo)
			.orElseThrow();

		assertThat(period.from()).isEqualTo(Instant.parse("2018-11-04T03:00:00Z"));
		assertThat(period.to()).isEqualTo(Instant.parse("2018-11-05T02:00:00Z"));
	}

	@Test
	void aDateThatDoesNotExistInTheZoneGivesNoPeriod() {
		ZoneId apia = ZoneId.of("Pacific/Apia");
		// 사모아는 2011-12-30 을 건너뛰었다 — 그날과 다음 날의 자정이 같은 시각이다
		assertThat(LocalDate.of(2011, 12, 30).atStartOfDay(apia).toInstant())
			.isEqualTo(LocalDate.of(2011, 12, 31).atStartOfDay(apia).toInstant());

		assertThat(EvaluationPeriod.ofDays(LocalDate.of(2011, 12, 30), LocalDate.of(2011, 12, 30), apia)).isEmpty();
		assertThat(EvaluationPeriod.ofDays(LocalDate.of(2011, 12, 31), LocalDate.of(2011, 12, 31), apia)).hasValue(
				new EvaluationPeriod(Instant.parse("2011-12-30T10:00:00Z"), Instant.parse("2011-12-31T10:00:00Z")));
	}

	@Test
	void aDateRangeEndingOnTheLastRepresentableDateGivesNoPeriod() {
		assertThat(EvaluationPeriod.ofDays(LocalDate.MAX, LocalDate.MAX, SEOUL)).isEmpty();
	}

	@Test
	void aTimeRangeIsAtMostThirtyTwoDaysLongAndStartsBeforeItEnds() {
		Instant from = Instant.parse("2026-10-01T00:00:00Z");

		assertThat(EvaluationPeriod.of(from, Instant.parse("2026-11-02T00:00:00Z"))).isPresent();
		assertThat(EvaluationPeriod.of(from, Instant.parse("2026-11-02T00:00:01Z"))).isEmpty();
		assertThat(EvaluationPeriod.of(from, from)).isEmpty();
		assertThat(EvaluationPeriod.of(from, Instant.parse("2026-09-30T00:00:00Z"))).isEmpty();
	}

	@Test
	void theConstructorRejectsWhatOfRejects() {
		Instant from = Instant.parse("2026-10-01T00:00:00Z");

		assertThatIllegalArgumentException().isThrownBy(() -> new EvaluationPeriod(from, from));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new EvaluationPeriod(from, Instant.parse("2026-11-02T00:00:01Z")));
	}

}
