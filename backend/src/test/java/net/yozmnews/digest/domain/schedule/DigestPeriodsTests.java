package net.yozmnews.digest.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import net.yozmnews.digest.domain.digest.DigestPeriod;
import org.junit.jupiter.api.Test;

/**
 * 다이제스트 기간 산식 — 처음은 24시간, 아니면 직전 끝부터, 72시간 상한, 빈 기간은 없음. 스프링 없이 돈다. 기대 시각은 손으로 셈한 리터럴이다.
 */
class DigestPeriodsTests {

	private static final Instant END = Instant.parse("2026-11-18T22:00:00Z");

	@Test
	void theFirstPeriodIsTheLastDay() {
		assertThat(DigestPeriods.next(END, null))
			.contains(new DigestPeriod(Instant.parse("2026-11-17T22:00:00Z"), END));
	}

	@Test
	void aLaterPeriodStartsAtTheLastCoveredEnd() {
		// 직전 끝이 30시간 전(실패 하루를 덮는다)
		assertThat(DigestPeriods.next(END, Instant.parse("2026-11-17T16:00:00Z")))
			.contains(new DigestPeriod(Instant.parse("2026-11-17T16:00:00Z"), END));
	}

	@Test
	void aPeriodIsAtMostThreeDays() {
		// 직전 끝이 100시간 전 — 끝에서 72시간까지로 줄인다
		assertThat(DigestPeriods.next(END, Instant.parse("2026-11-14T18:00:00Z")))
			.contains(new DigestPeriod(Instant.parse("2026-11-15T22:00:00Z"), END));
		// 대조 — 정확히 72시간 전이면 그대로
		assertThat(DigestPeriods.next(END, Instant.parse("2026-11-15T22:00:00Z")))
			.contains(new DigestPeriod(Instant.parse("2026-11-15T22:00:00Z"), END));
	}

	@Test
	void anEmptyPeriodIsNone() {
		assertThat(DigestPeriods.next(END, END)).isEmpty();
		assertThat(DigestPeriods.next(END, Instant.parse("2026-11-18T22:00:01Z"))).isEmpty();
	}

	@Test
	void aZoneChangeThatMovesTheNextEndBeforeTheLastEndGivesNoPeriod() {
		// UTC−12 의 23:59 로 2026-11-18 을 만든 뒤 UTC+14 의 00:00 으로 바꾸면 2026-11-19 의 끝이 앞선다
		Instant last = DigestScheduleRules.generationTime(LocalDate.parse("2026-11-18"), LocalTime.of(23, 59),
				ZoneId.of("Etc/GMT+12"));
		Instant next = DigestScheduleRules.generationTime(LocalDate.parse("2026-11-19"), LocalTime.MIDNIGHT,
				ZoneId.of("Pacific/Kiritimati"));
		assertThat(last).isEqualTo(Instant.parse("2026-11-19T11:59:00Z"));
		assertThat(next).isEqualTo(Instant.parse("2026-11-18T10:00:00Z"));

		assertThat(DigestPeriods.next(next, last)).isEmpty();
	}

}
