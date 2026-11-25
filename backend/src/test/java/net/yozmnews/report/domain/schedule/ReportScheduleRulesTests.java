package net.yozmnews.report.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.domain.report.ReportLedger.Weekly;
import net.yozmnews.report.domain.report.ReportPeriod;
import net.yozmnews.report.domain.schedule.ReportScheduleRules.MonthlyReadiness;
import org.junit.jupiter.api.Test;

/**
 * 리포트 일정 규칙 — 주간 · 월간 기간의 경계와 창, 설정을 바꾼 뒤의 기간, 서머타임 · 달 경계, 생성 조건, 월간의 기다림. 기대값은 손으로 센
 * 리터럴이다(서울은 UTC+9, 뉴욕은 3월 8일 전 UTC−5 · 뒤 UTC−4 · 11월 1일 뒤 UTC−5).
 */
class ReportScheduleRulesTests {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

	private static final LocalTime SEVEN = LocalTime.of(7, 0);

	/** 2026-11-23(월) 07:00 KST */
	private static final Instant MONDAY = Instant.parse("2026-11-22T22:00:00Z");

	@Test
	void aWeeklyPeriodEndsAtTheChosenDaysDigestTimeAndIsMadeInTheTwoHoursAfter() {
		ReportPeriod week = new ReportPeriod(ReportPeriodKind.WEEK, Instant.parse("2026-11-15T22:00:00Z"), MONDAY);

		assertThat(ReportScheduleRules.dueWeekly(MONDAY, SEOUL, SEVEN, DayOfWeek.MONDAY, null)).contains(week);
		assertThat(ReportScheduleRules.dueWeekly(MONDAY.plus(Duration.ofHours(2)).minusSeconds(1), SEOUL, SEVEN,
				DayOfWeek.MONDAY, null))
			.contains(week);
		// 창이 지나면 만들지 않는다 · 경계 바로 앞은 지난주 경계의 창 밖이다
		assertThat(ReportScheduleRules.dueWeekly(MONDAY.plus(Duration.ofHours(2)), SEOUL, SEVEN, DayOfWeek.MONDAY, null))
			.isEmpty();
		assertThat(ReportScheduleRules.dueWeekly(MONDAY.minusSeconds(1), SEOUL, SEVEN, DayOfWeek.MONDAY, null)).isEmpty();
	}

	@Test
	void aWeeklyPeriodIsMadeOnlyWhenItEndsAfterTheLatestReportOfItsKind() {
		assertThat(ReportScheduleRules.dueWeekly(MONDAY, SEOUL, SEVEN, DayOfWeek.MONDAY, MONDAY)).isEmpty();
		assertThat(ReportScheduleRules.dueWeekly(MONDAY, SEOUL, SEVEN, DayOfWeek.MONDAY, MONDAY.plusSeconds(1)))
			.isEmpty();
		// 대조 — 지난주 리포트가 마지막이면 만든다
		assertThat(ReportScheduleRules.dueWeekly(MONDAY, SEOUL, SEVEN, DayOfWeek.MONDAY,
				Instant.parse("2026-11-15T22:00:00Z")))
			.isPresent();
	}

	@Test
	void afterTheDayChangesTheWholeNewPeriodIsMadeEvenIfItOverlapsAndAPassedDayLeavesAGap() {
		// 월요일 리포트(끝 11-23 07:00 KST)가 마지막인데 수요일에 금요일로 바꿨다 — 금요일 07:00 의 온전한 7일, 월 ~ 금 3일이 겹친다
		Instant friday = Instant.parse("2026-11-26T22:00:00Z");
		assertThat(ReportScheduleRules.dueWeekly(friday, SEOUL, SEVEN, DayOfWeek.FRIDAY, MONDAY))
			.contains(new ReportPeriod(ReportPeriodKind.WEEK, Instant.parse("2026-11-19T22:00:00Z"), friday));
		// 수요일 12:00 KST 에 화요일로 바꿨다 — 이번 화요일 경계(11-24 07:00)의 창이 지나 만들지 않는다(틈)
		assertThat(ReportScheduleRules.dueWeekly(Instant.parse("2026-11-25T03:00:00Z"), SEOUL, SEVEN, DayOfWeek.TUESDAY,
				MONDAY))
			.isEmpty();
	}

	@Test
	void aWeekAcrossDaylightSavingIsOneHourShorterOrLonger() {
		// 2026-03-08 일요일 07:00 EDT(11:00Z) — 시작 03-01 07:00 EST(12:00Z), 167시간
		Instant spring = Instant.parse("2026-03-08T11:00:00Z");
		ReportPeriod shorter = ReportScheduleRules.dueWeekly(spring, NEW_YORK, SEVEN, DayOfWeek.SUNDAY, null)
			.orElseThrow();
		assertThat(shorter.start()).isEqualTo(Instant.parse("2026-03-01T12:00:00Z"));
		assertThat(Duration.between(shorter.start(), shorter.end())).isEqualTo(Duration.ofHours(167));
		// 2026-11-08 일요일 01:30 EST(06:30Z) — 시작 11-01 01:30 은 두 번 있어 앞 오프셋 EDT(05:30Z), 169시간
		Instant fall = Instant.parse("2026-11-08T06:30:00Z");
		ReportPeriod longer = ReportScheduleRules.dueWeekly(fall, NEW_YORK, LocalTime.of(1, 30), DayOfWeek.SUNDAY, null)
			.orElseThrow();
		assertThat(longer.start()).isEqualTo(Instant.parse("2026-11-01T05:30:00Z"));
		assertThat(Duration.between(longer.start(), longer.end())).isEqualTo(Duration.ofHours(169));
		// 없는 시각(03-08 02:30)은 틈만큼 뒤 — 03:30 EDT(07:30Z)
		assertThat(ReportScheduleRules.boundary(LocalDate.of(2026, 3, 8), LocalTime.of(2, 30), NEW_YORK))
			.isEqualTo(Instant.parse("2026-03-08T07:30:00Z"));
	}

	@Test
	void aMonthlyPeriodRunsFromTheChosenDateOfTheMonthBeforeAndAMissingDayIsTheLastDay() {
		// 31일을 고른 11월(30일까지) — 경계 11-30 07:00 KST, 시작 10-31 07:00 KST
		Instant november = Instant.parse("2026-11-29T22:00:00Z");
		assertThat(ReportScheduleRules.dueMonthly(november, SEOUL, SEVEN, true, true, 31, null))
			.contains(new ReportPeriod(ReportPeriodKind.MONTH, Instant.parse("2026-10-30T22:00:00Z"), november));
		// 윤년 2월 — 경계 2028-02-29, 시작 01-31
		Instant leap = Instant.parse("2028-02-28T22:00:00Z");
		assertThat(ReportScheduleRules.dueMonthly(leap, SEOUL, SEVEN, true, true, 31, null))
			.contains(new ReportPeriod(ReportPeriodKind.MONTH, Instant.parse("2028-01-30T22:00:00Z"), leap));
		// 이달 경계(19일) 전이면 지난달 경계의 창은 이미 지났다
		assertThat(ReportScheduleRules.dueMonthly(Instant.parse("2026-11-15T00:00:00Z"), SEOUL, SEVEN, true, true, 19,
				null))
			.isEmpty();
	}

	@Test
	void noMonthlyReportWhenWeeklyReportsAreOff() {
		Instant first = Instant.parse("2026-11-30T22:00:00Z");
		assertThat(ReportScheduleRules.dueMonthly(first, SEOUL, SEVEN, false, true, 1, null)).isEmpty();
		assertThat(ReportScheduleRules.dueMonthly(first, SEOUL, SEVEN, true, false, 1, null)).isEmpty();
		// 대조 — 둘 다 켜면 12-01 07:00 KST 경계의 월간
		assertThat(ReportScheduleRules.dueMonthly(first, SEOUL, SEVEN, true, true, 1, null))
			.contains(new ReportPeriod(ReportPeriodKind.MONTH, Instant.parse("2026-10-31T22:00:00Z"), first));
	}

	@Test
	void theMonthlyDateIsTheChosenDayOrTheLastDayOfAShorterMonth() {
		assertThat(ReportScheduleRules.monthlyDate(YearMonth.of(2026, 1), 31)).isEqualTo(LocalDate.of(2026, 1, 31));
		assertThat(ReportScheduleRules.monthlyDate(YearMonth.of(2026, 4), 31)).isEqualTo(LocalDate.of(2026, 4, 30));
		assertThat(ReportScheduleRules.monthlyDate(YearMonth.of(2026, 2), 29)).isEqualTo(LocalDate.of(2026, 2, 28));
		assertThat(ReportScheduleRules.monthlyDate(YearMonth.of(2028, 2), 30)).isEqualTo(LocalDate.of(2028, 2, 29));
		assertThat(ReportScheduleRules.monthlyDate(YearMonth.of(2026, 2), 1)).isEqualTo(LocalDate.of(2026, 2, 1));
	}

	@Test
	void aWeeklyReportCountsForAMonthOnlyWhenItLiesWhollyInside() {
		ReportPeriod month = new ReportPeriod(ReportPeriodKind.MONTH, Instant.parse("2026-10-31T22:00:00Z"),
				Instant.parse("2026-11-30T22:00:00Z"));
		assertThat(ReportScheduleRules.within(new ReportPeriod(ReportPeriodKind.WEEK,
				Instant.parse("2026-11-23T22:00:00Z"), Instant.parse("2026-11-30T22:00:00Z")), month)).isTrue();
		assertThat(ReportScheduleRules.within(new ReportPeriod(ReportPeriodKind.WEEK,
				Instant.parse("2026-10-31T22:00:00Z"), Instant.parse("2026-11-07T22:00:00Z")), month)).isTrue();
		assertThat(ReportScheduleRules.within(new ReportPeriod(ReportPeriodKind.WEEK,
				Instant.parse("2026-10-26T22:00:00Z"), Instant.parse("2026-11-02T22:00:00Z")), month)).isFalse();
		assertThat(ReportScheduleRules.within(new ReportPeriod(ReportPeriodKind.WEEK,
				Instant.parse("2026-11-28T22:00:00Z"), Instant.parse("2026-12-05T22:00:00Z")), month)).isFalse();
	}

	@Test
	void aMonthWaitsForCountsAndCarriesOnlyTheReadyWeekliesWhollyInside() {
		ReportPeriod month = new ReportPeriod(ReportPeriodKind.MONTH, Instant.parse("2026-10-31T22:00:00Z"),
				Instant.parse("2026-11-30T22:00:00Z"));
		// 걸친 앞 주(10-26 ~ 11-02) · 안의 READY 둘 · 안의 FAILED · 걸친 뒤 주(11-28 ~ 12-05)
		List<Weekly> overlapping = List.of(
				weekly(1, "2026-10-26T22:00:00Z", "2026-11-02T22:00:00Z", ReportStatus.SCHEDULED),
				weekly(2, "2026-11-02T22:00:00Z", "2026-11-09T22:00:00Z", ReportStatus.READY),
				weekly(3, "2026-11-09T22:00:00Z", "2026-11-16T22:00:00Z", ReportStatus.FAILED),
				weekly(4, "2026-11-23T22:00:00Z", "2026-11-30T22:00:00Z", ReportStatus.READY),
				weekly(5, "2026-11-28T22:00:00Z", "2026-12-05T22:00:00Z", ReportStatus.RUNNING));

		assertThat(ReportScheduleRules.monthlyWeeklies(overlapping, month)).extracting(Weekly::id)
			.containsExactly(new ReportId(2), new ReportId(3), new ReportId(4));
		assertThat(ReportScheduleRules.monthlyInputs(overlapping, month)).containsExactly(new ReportId(2),
				new ReportId(4));
		// 걸친 주간은 끝나지 않았어도 기다리지 않는다 — 안의 주간만 본다
		assertThat(ReportScheduleRules.monthlyReadiness(ReportScheduleRules.monthlyWeeklies(overlapping, month)
			.stream()
			.map(Weekly::status)
			.toList())).isEqualTo(MonthlyReadiness.MAKE);
		assertThat(ReportScheduleRules.countsForMonthly(ReportStatus.READY)).isTrue();
		assertThat(ReportScheduleRules.countsForMonthly(ReportStatus.EMPTY)).isFalse();
		assertThat(ReportScheduleRules.countsForMonthly(ReportStatus.FAILED)).isFalse();
	}

	@Test
	void aMonthWaitsForUnfinishedWeekliesAndNeedsTwoReadyOnes() {
		assertThat(ReportScheduleRules.monthlyReadiness(List.of(ReportStatus.READY, ReportStatus.READY)))
			.isEqualTo(MonthlyReadiness.MAKE);
		assertThat(ReportScheduleRules
			.monthlyReadiness(List.of(ReportStatus.READY, ReportStatus.FAILED, ReportStatus.EMPTY)))
			.isEqualTo(MonthlyReadiness.SKIP);
		assertThat(ReportScheduleRules.monthlyReadiness(List.of(ReportStatus.READY, ReportStatus.READY,
				ReportStatus.SCHEDULED)))
			.isEqualTo(MonthlyReadiness.WAIT);
		assertThat(ReportScheduleRules.monthlyReadiness(List.of(ReportStatus.RUNNING))).isEqualTo(MonthlyReadiness.WAIT);
		assertThat(ReportScheduleRules.monthlyReadiness(List.of())).isEqualTo(MonthlyReadiness.SKIP);
	}

	@Test
	void aWeekNeedsThreeReadyDigestDaysAndTheLeaseCoversTwoGenerationCalls() {
		assertThat(ReportScheduleRules.enoughDigestDays(2)).isFalse();
		assertThat(ReportScheduleRules.enoughDigestDays(3)).isTrue();
		assertThat(ReportScheduleRules.RUN_LEASE).isEqualTo(Duration.ofMinutes(40));
		assertThat(ReportScheduleRules.CATCH_UP).isEqualTo(Duration.ofHours(2));
	}

	private static Weekly weekly(long id, String start, String end, ReportStatus status) {
		return new Weekly(new ReportId(id),
				new ReportPeriod(ReportPeriodKind.WEEK, Instant.parse(start), Instant.parse(end)), status);
	}

}
