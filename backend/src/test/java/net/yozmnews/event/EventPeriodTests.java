package net.yozmnews.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * 사건 조회의 기간과 사건 ID — 기간은 시작이 끝보다 앞이고 31일까지, 사건 ID 는 1 이상.
 * <p>
 * 지키는 규칙: event-R20
 */
class EventPeriodTests {

	private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");

	@Test
	void aPeriodIsAtMostThirtyOneDays() {
		assertThat(EventPeriod.of(FROM, Instant.parse("2026-12-02T00:00:00Z"))).isPresent();
		assertThat(EventPeriod.of(FROM, Instant.parse("2026-12-02T00:00:00.000001Z"))).isEmpty();
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new EventPeriod(FROM, Instant.parse("2026-12-02T00:00:00.000001Z")));
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
