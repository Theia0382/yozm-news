package net.yozmnews.report.domain.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/**
 * 기간 날짜의 정본 — 첫날은 시작 순간의 날짜, 마지막 날은 끝 바로 앞 순간의 날짜. 지키는 규칙: report-R26
 */
class ReportPeriodTests {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Test
	void aPeriodEndingAtMidnightInTheZoneEndsOnTheDayBefore() {
		// 서울 11월 24일 0시 ~ 12월 1일 0시
		Instant start = Instant.parse("2026-11-23T15:00:00Z");
		Instant end = Instant.parse("2026-11-30T15:00:00Z");

		assertThat(ReportPeriod.firstDay(start, SEOUL)).isEqualTo(LocalDate.parse("2026-11-24"));
		assertThat(ReportPeriod.lastDay(end, SEOUL)).isEqualTo(LocalDate.parse("2026-11-30"));
	}

	@Test
	void aPeriodEndingAfterMidnightEndsOnThatDay() {
		Instant end = Instant.parse("2026-11-30T15:00:00.000000001Z");

		assertThat(ReportPeriod.lastDay(end, SEOUL)).isEqualTo(LocalDate.parse("2026-12-01"));
	}

	@Test
	void theSameInstantsGiveOtherDaysInAnotherZone() {
		Instant start = Instant.parse("2026-11-23T15:00:00Z");
		Instant end = Instant.parse("2026-11-30T15:00:00Z");

		assertThat(ReportPeriod.firstDay(start, ZoneOffset.UTC)).isEqualTo(LocalDate.parse("2026-11-23"));
		assertThat(ReportPeriod.lastDay(end, ZoneOffset.UTC)).isEqualTo(LocalDate.parse("2026-11-30"));
	}

}
