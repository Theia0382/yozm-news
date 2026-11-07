package net.yozmnews.event.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;

import net.yozmnews.event.EventStatus;

import org.junit.jupiter.api.Test;

/**
 * 사건 닫기 판정 — 마지막으로 붙은 뒤 닫는 기간(7일) 또는 연 뒤 최대 기간(14일)이 지난 열린 사건을 닫는다. 경계는 포함한다.
 * <p>
 * 지키는 규칙: event-R13
 */
class EventClosingTests {

	private static final EventClosing CLOSING = EventClosing.of(Duration.ofDays(7), Duration.ofDays(14),
			Duration.ofHours(72));

	private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");

	@Test
	void anEventIdleForTheClosingPeriodIsDueAndOneSecondLessIsNot() {
		assertThat(CLOSING.due(event(EventStatus.OPEN, "2026-11-15T00:00:00Z", "2026-11-13T00:00:00Z"), NOW)).isTrue();
		assertThat(CLOSING.due(event(EventStatus.OPEN, "2026-11-15T00:00:00Z", "2026-11-13T00:00:01Z"), NOW)).isFalse();
	}

	@Test
	void anEventOpenForTheMaximumIsDueEvenWithARecentArticle() {
		assertThat(CLOSING.due(event(EventStatus.OPEN, "2026-11-06T00:00:00Z", "2026-11-19T00:00:00Z"), NOW)).isTrue();
		assertThat(CLOSING.due(event(EventStatus.OPEN, "2026-11-06T00:00:01Z", "2026-11-19T00:00:00Z"), NOW)).isFalse();
	}

	@Test
	void onlyOpenEventsAreDue() {
		assertThat(CLOSING.due(event(EventStatus.CLOSED, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z"), NOW)).isFalse();
		assertThat(CLOSING.due(event(EventStatus.MERGED, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z"), NOW)).isFalse();
	}

	@Test
	void theCutoffsAreNowMinusThePeriods() {
		assertThat(CLOSING.idleBefore(NOW)).isEqualTo(Instant.parse("2026-11-13T00:00:00Z"));
		assertThat(CLOSING.openedBefore(NOW)).isEqualTo(Instant.parse("2026-11-06T00:00:00Z"));
	}

	@Test
	void periodsBreakingTheRelationsAreRejected() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> EventClosing.of(Duration.ofHours(48), Duration.ofDays(14), Duration.ofHours(72)));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> EventClosing.of(Duration.ofDays(7), Duration.ofDays(6), Duration.ofHours(72)));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> EventClosing.of(Duration.ofDays(7).plusMillis(1), Duration.ofDays(14), Duration.ofHours(72)));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> EventClosing.of(Duration.ZERO, Duration.ZERO, Duration.ofHours(72)));
		assertThat(EventClosing.of(Duration.ofHours(72), Duration.ofHours(72), Duration.ofHours(72)))
			.isEqualTo(new EventClosing(Duration.ofHours(72), Duration.ofHours(72)));
	}

	private static LockedEvent event(EventStatus status, String openedAt, String lastJoinedAt) {
		Instant t = Instant.parse("2026-11-01T00:00:00Z");
		return new LockedEvent(1, status, "model-a", new float[] { 1, 0 }, 1, t, t, Instant.parse(openedAt),
				Instant.parse(lastJoinedAt));
	}

}
