package net.yozmnews.account.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * 고정 창 — 유닉스 기원부터 길이로 나눈 칸이고, 시작은 포함 · 끝은 제외다.
 * <p>
 * 지키는 규칙: user-R39
 */
class LimitWindowTests {

	@Test
	void lastSecondBeforeTheBoundaryBelongsToTheEndingWindow() {
		assertThat(LimitWindow.containing(Instant.parse("2026-11-14T10:09:59Z"), Duration.parse("PT10M")))
			.isEqualTo(new LimitWindow(Instant.parse("2026-11-14T10:00:00Z"), Instant.parse("2026-11-14T10:10:00Z")));
	}

	@Test
	void boundaryItselfStartsTheNextWindow() {
		assertThat(LimitWindow.containing(Instant.parse("2026-11-14T10:10:00Z"), Duration.parse("PT10M")))
			.isEqualTo(new LimitWindow(Instant.parse("2026-11-14T10:10:00Z"), Instant.parse("2026-11-14T10:20:00Z")));
	}

	@Test
	void hourWindowStartsOnTheHourWhateverTheFraction() {
		assertThat(LimitWindow.containing(Instant.parse("2026-11-14T10:59:59.999Z"), Duration.parse("PT1H")))
			.isEqualTo(new LimitWindow(Instant.parse("2026-11-14T10:00:00Z"), Instant.parse("2026-11-14T11:00:00Z")));
	}

}
