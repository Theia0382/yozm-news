package net.yozmnews.digest.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import net.yozmnews.digest.domain.digest.DigestPeriod;
import org.junit.jupiter.api.Test;

/**
 * 수동 생성의 대상 — 늦은 날짜가 있으면 그것을 다시, 오늘 다이제스트가 있으면 같은 기간으로 다시, 없으면 오늘 날짜의 새 기간(끝은 요청 시각),
 * 새 기간이 비면 가장 늦은 다이제스트를 다시. 스프링 없이 돈다. 기대값은 손으로 쓴 리터럴이다.
 */
class ManualTargetTests {

	private static final LocalDate TODAY = LocalDate.parse("2026-11-19");

	private static final Instant NOW = Instant.parse("2026-11-19T03:00:00Z");

	private static final DigestPeriod TODAY_PERIOD = new DigestPeriod(Instant.parse("2026-11-17T22:00:00Z"),
			Instant.parse("2026-11-18T22:00:00Z"));

	private static final DigestPeriod YESTERDAY_PERIOD = new DigestPeriod(Instant.parse("2026-11-16T22:00:00Z"),
			Instant.parse("2026-11-17T22:00:00Z"));

	@Test
	void aFirstRequestMakesTodayWithTheLastDay() {
		assertThat(ManualTarget.of(TODAY, NOW, null, null, null, null)).isEqualTo(new ManualTarget(TODAY,
				new DigestPeriod(Instant.parse("2026-11-18T03:00:00Z"), NOW), false));
	}

	@Test
	void withoutADigestTodayTheNewPeriodStartsAtTheLastCoveredEnd() {
		assertThat(ManualTarget.of(TODAY, NOW, LocalDate.parse("2026-11-18"), YESTERDAY_PERIOD, null,
				Instant.parse("2026-11-17T22:00:00Z")))
			.isEqualTo(new ManualTarget(TODAY, new DigestPeriod(Instant.parse("2026-11-17T22:00:00Z"), NOW), false));
	}

	@Test
	void aDigestOfTodayIsMadeAgainWithItsPeriod() {
		assertThat(ManualTarget.of(TODAY, NOW, TODAY, TODAY_PERIOD, TODAY_PERIOD, Instant.parse("2026-11-18T22:00:00Z")))
			.isEqualTo(new ManualTarget(TODAY, TODAY_PERIOD, true));
	}

	@Test
	void aLaterDateAfterAZoneChangeIsMadeAgainRatherThanGoingBack() {
		DigestPeriod later = new DigestPeriod(Instant.parse("2026-11-18T22:00:00Z"), Instant.parse("2026-11-19T02:00:00Z"));

		assertThat(ManualTarget.of(TODAY, NOW, LocalDate.parse("2026-11-20"), later, null, null))
			.isEqualTo(new ManualTarget(LocalDate.parse("2026-11-20"), later, true));
	}

	@Test
	void anEmptyNewPeriodMakesTheLatestDigestAgain() {
		// 직전 끝이 요청 시각보다 늦다(프로세스 사이 시계 차이)
		assertThat(ManualTarget.of(TODAY, NOW, LocalDate.parse("2026-11-18"), YESTERDAY_PERIOD, null,
				Instant.parse("2026-11-19T03:00:01Z")))
			.isEqualTo(new ManualTarget(LocalDate.parse("2026-11-18"), YESTERDAY_PERIOD, true));
	}

}
