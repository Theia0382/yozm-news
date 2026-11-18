package net.yozmnews.digest.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/**
 * 다이제스트 일정 규칙 — 날짜의 생성 시각(서머타임의 틈 · 겹침 포함)과 일정이 지금 만들 날짜(생성 시각 뒤 2시간 창 · 자정 근처 · 가장 늦은
 * 날짜). 스프링 없이 돈다. 기대 시각은 손으로 셈한 리터럴이다.
 */
class DigestScheduleRulesTests {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

	private static final LocalTime SEVEN = LocalTime.of(7, 0);

	@Test
	void theGenerationTimeOfADateInItsZone() {
		// 서울 07:00 = 전날 22:00 UTC
		assertThat(DigestScheduleRules.generationTime(LocalDate.parse("2026-11-19"), SEVEN, SEOUL))
			.isEqualTo(Instant.parse("2026-11-18T22:00:00Z"));
	}

	@Test
	void aTimeInTheSpringGapMovesLaterAndATimeInTheFallOverlapTakesTheEarlierOffset() {
		// 2026-03-08 02:00 EST → 03:00 EDT — 02:30 은 없는 시각이라 03:30 EDT(UTC−4) = 07:30 UTC
		assertThat(DigestScheduleRules.generationTime(LocalDate.parse("2026-03-08"), LocalTime.of(2, 30), NEW_YORK))
			.isEqualTo(Instant.parse("2026-03-08T07:30:00Z"));
		// 2026-11-01 02:00 EDT → 01:00 EST — 01:30 은 두 번 있어 앞 오프셋 EDT(UTC−4) = 05:30 UTC
		assertThat(DigestScheduleRules.generationTime(LocalDate.parse("2026-11-01"), LocalTime.of(1, 30), NEW_YORK))
			.isEqualTo(Instant.parse("2026-11-01T05:30:00Z"));
		// 대조 — 서머타임이 아닌 날의 01:30 은 EST(UTC−5) = 06:30 UTC
		assertThat(DigestScheduleRules.generationTime(LocalDate.parse("2026-11-02"), LocalTime.of(1, 30), NEW_YORK))
			.isEqualTo(Instant.parse("2026-11-02T06:30:00Z"));
	}

	@Test
	void theDateIsDueFromItsGenerationTimeForTwoHours() {
		// 2026-11-19 07:00 KST = 2026-11-18T22:00:00Z
		assertThat(DigestScheduleRules.dueDate(Instant.parse("2026-11-18T21:59:00Z"), SEVEN, SEOUL, null)).isEmpty();
		assertThat(DigestScheduleRules.dueDate(Instant.parse("2026-11-18T22:00:00Z"), SEVEN, SEOUL, null))
			.contains(LocalDate.parse("2026-11-19"));
		assertThat(DigestScheduleRules.dueDate(Instant.parse("2026-11-18T23:59:00Z"), SEVEN, SEOUL, null))
			.contains(LocalDate.parse("2026-11-19"));
		assertThat(DigestScheduleRules.dueDate(Instant.parse("2026-11-19T00:00:00Z"), SEVEN, SEOUL, null)).isEmpty();
	}

	@Test
	void nearMidnightTheDueDateIsTheDayBeforeTheLocalToday() {
		// 23:30 KST 의 2026-11-18 = 2026-11-18T14:30:00Z. 지금 00:10 KST 의 2026-11-19 — 오늘의 23:30 은 아직이고 전날의 창 안이다
		LocalTime late = LocalTime.of(23, 30);
		assertThat(DigestScheduleRules.dueDate(Instant.parse("2026-11-18T15:10:00Z"), late, SEOUL, null))
			.contains(LocalDate.parse("2026-11-18"));
	}

	@Test
	void aDateNotAfterTheLatestDigestDateIsNotDue() {
		Instant now = Instant.parse("2026-11-18T22:30:00Z");
		assertThat(DigestScheduleRules.dueDate(now, SEVEN, SEOUL, LocalDate.parse("2026-11-19"))).isEmpty();
		assertThat(DigestScheduleRules.dueDate(now, SEVEN, SEOUL, LocalDate.parse("2026-11-20"))).isEmpty();
		// 대조 — 가장 늦은 날짜가 앞이면 만든다
		assertThat(DigestScheduleRules.dueDate(now, SEVEN, SEOUL, LocalDate.parse("2026-11-18")))
			.contains(LocalDate.parse("2026-11-19"));
	}

	@Test
	void theLeaseCoversTwoLongestGenerationCallsAndTenMinutes() {
		// 생성 호출 하나의 최악 15분 × 2(다시 묻기) + 10분
		assertThat(DigestScheduleRules.RUN_LEASE).isEqualTo(Duration.ofMinutes(40));
	}

	@Test
	void theDayStartsAtLocalMidnight() {
		// 2026-11-19 0시 PST(UTC−8) = 08:00 UTC, 서머타임 첫날 2026-03-08 의 0시는 아직 PST
		assertThat(DigestScheduleRules.startOfDay(LocalDate.parse("2026-11-19"), ZoneId.of("America/Los_Angeles")))
			.isEqualTo(Instant.parse("2026-11-19T08:00:00Z"));
		assertThat(DigestScheduleRules.startOfDay(LocalDate.parse("2026-03-08"), ZoneId.of("America/Los_Angeles")))
			.isEqualTo(Instant.parse("2026-03-08T08:00:00Z"));
		assertThat(DigestScheduleRules.startOfDay(LocalDate.parse("2026-03-09"), ZoneId.of("America/Los_Angeles")))
			.isEqualTo(Instant.parse("2026-03-09T07:00:00Z"));
	}

}
