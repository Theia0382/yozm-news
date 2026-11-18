package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import net.yozmnews.digest.InterestHistorySpan;
import org.junit.jupiter.api.Test;

/**
 * 관심사 히스토리의 기간 경계 — 날짜 · 시각은 달력으로 센 리터럴이다.
 */
class InterestPeriodsTests {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	/** 2026-11-18(수) 12:00 서울 */
	private static final Instant WEDNESDAY_NOON = Instant.parse("2026-11-18T03:00:00Z");

	@Test
	void weeksStartOnMondayAndTheLastIsInProgress() {
		List<InterestPeriod> periods = InterestPeriods.recent(SEOUL, InterestHistorySpan.WEEKS_13, WEDNESDAY_NOON);

		assertThat(periods).hasSize(14);
		InterestPeriod last = periods.get(13);
		assertThat(last.firstDay()).isEqualTo(LocalDate.of(2026, 11, 16));
		assertThat(last.lastDay()).isEqualTo(LocalDate.of(2026, 11, 22));
		assertThat(last.start()).isEqualTo(Instant.parse("2026-11-15T15:00:00Z"));
		assertThat(last.end()).isEqualTo(Instant.parse("2026-11-22T15:00:00Z"));
		assertThat(last.inProgress()).isTrue();
		assertThat(last.reference()).as("진행 중인 기간의 기준 시각은 부르는 시각").isEqualTo(WEDNESDAY_NOON);

		InterestPeriod comparison = periods.get(0);
		assertThat(comparison.firstDay()).as("견줌용").isEqualTo(LocalDate.of(2026, 8, 17));
		assertThat(comparison.lastDay()).isEqualTo(LocalDate.of(2026, 8, 23));
		assertThat(periods.get(1).firstDay()).as("보일 첫 기간").isEqualTo(LocalDate.of(2026, 8, 24));
		assertThat(periods.get(1).lastDay()).isEqualTo(LocalDate.of(2026, 8, 30));

		InterestPeriod previous = periods.get(12);
		assertThat(previous.firstDay()).isEqualTo(LocalDate.of(2026, 11, 9));
		assertThat(previous.inProgress()).isFalse();
		assertThat(previous.reference()).as("지난 기간의 기준 시각은 기간 끝").isEqualTo(Instant.parse("2026-11-15T15:00:00Z"));
		assertThat(periods.subList(0, 13)).noneMatch(InterestPeriod::inProgress);
	}

	@Test
	void monthsStartOnTheFirst() {
		List<InterestPeriod> periods = InterestPeriods.recent(SEOUL, InterestHistorySpan.MONTHS_12, WEDNESDAY_NOON);

		assertThat(periods).hasSize(13);
		InterestPeriod last = periods.get(12);
		assertThat(last.firstDay()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(last.lastDay()).isEqualTo(LocalDate.of(2026, 11, 30));
		assertThat(last.reference()).isEqualTo(WEDNESDAY_NOON);
		assertThat(periods.get(11).firstDay()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(periods.get(11).lastDay()).isEqualTo(LocalDate.of(2026, 10, 31));
		assertThat(periods.get(11).reference()).isEqualTo(Instant.parse("2026-10-31T15:00:00Z"));
		assertThat(periods.get(0).firstDay()).as("견줌용").isEqualTo(LocalDate.of(2025, 11, 1));
		assertThat(periods.get(0).lastDay()).isEqualTo(LocalDate.of(2025, 11, 30));
		assertThat(periods.get(3).lastDay()).as("2026년 2월").isEqualTo(LocalDate.of(2026, 2, 28));
	}

	@Test
	void aWeekAcrossTheDaylightSavingStartIsAnHourShorter() {
		// 뉴욕 2027-03-14(일) 02:00 에 서머타임 시작 — 03-08 00:00 EST(−5) ~ 03-15 00:00 EDT(−4)
		ZoneId newYork = ZoneId.of("America/New_York");
		List<InterestPeriod> periods = InterestPeriods.recent(newYork, InterestHistorySpan.WEEKS_13,
				Instant.parse("2027-03-16T12:00:00Z"));

		InterestPeriod week = periods.get(12);
		assertThat(week.firstDay()).isEqualTo(LocalDate.of(2027, 3, 8));
		assertThat(week.start()).isEqualTo(Instant.parse("2027-03-08T05:00:00Z"));
		assertThat(week.end()).isEqualTo(Instant.parse("2027-03-15T04:00:00Z"));
		assertThat(periods.get(13).start()).isEqualTo(Instant.parse("2027-03-15T04:00:00Z"));
	}

	@Test
	void callingAtTheVeryStartOfAWeekOpensThatWeek() {
		// 2026-11-16(월) 00:00 서울
		Instant mondayMidnight = Instant.parse("2026-11-15T15:00:00Z");

		List<InterestPeriod> periods = InterestPeriods.recent(SEOUL, InterestHistorySpan.WEEKS_13, mondayMidnight);

		InterestPeriod last = periods.get(13);
		assertThat(last.firstDay()).isEqualTo(LocalDate.of(2026, 11, 16));
		assertThat(last.start()).isEqualTo(mondayMidnight);
		assertThat(last.reference()).isEqualTo(mondayMidnight);
		assertThat(periods.get(12).end()).isEqualTo(mondayMidnight);
	}

	@Test
	void theLongestSpansHaveOneMorePeriodForComparison() {
		assertThat(InterestPeriods.recent(SEOUL, InterestHistorySpan.WEEKS_52, WEDNESDAY_NOON)).hasSize(53);
		assertThat(InterestPeriods.recent(SEOUL, InterestHistorySpan.MONTHS_36, WEDNESDAY_NOON).get(0).firstDay())
			.isEqualTo(LocalDate.of(2023, 11, 1));
	}

	@Test
	void aPeriodRefusesAReferenceOutsideItAndAnEmptyRange() {
		LocalDate monday = LocalDate.of(2026, 11, 16);
		LocalDate sunday = LocalDate.of(2026, 11, 22);
		Instant start = Instant.parse("2026-11-15T15:00:00Z");
		Instant end = Instant.parse("2026-11-22T15:00:00Z");

		assertThat(new InterestPeriod(monday, sunday, start, end, start, true).reference()).as("시작 시각은 기간 안")
			.isEqualTo(start);
		Instant afterEnd = Instant.parse("2026-11-22T15:00:01Z");
		Instant beforeStart = Instant.parse("2026-11-15T14:59:59Z");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new InterestPeriod(monday, sunday, start, end, afterEnd, false));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new InterestPeriod(monday, sunday, start, end, beforeStart, false));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new InterestPeriod(monday, sunday, end, end, end, false));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new InterestPeriod(sunday, monday, start, end, end, false));
	}

}
