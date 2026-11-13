package net.yozmnews.user.domain.notice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * 반영 기준 — 기준이 없거나 기준보다 늦은 회차만 반영한다.
 * <p>
 * 지키는 규칙: user-R24
 */
class FeedStatusReceiptTests {

	private static final Instant THROUGH = Instant.parse("2026-11-13T01:00:00Z");

	@Test
	void acceptsAnyRunWhenNothingWasReceived() {
		assertThat(FeedStatusReceipt.accepts(null, Instant.parse("2026-11-13T00:00:00Z"))).isTrue();
	}

	@Test
	void acceptsOnlyARunAfterTheReceipt() {
		assertThat(FeedStatusReceipt.accepts(THROUGH, Instant.parse("2026-11-13T01:00:00Z"))).isFalse();
		assertThat(FeedStatusReceipt.accepts(THROUGH, Instant.parse("2026-11-13T00:59:59Z"))).isFalse();
		assertThat(FeedStatusReceipt.accepts(THROUGH, Instant.parse("2026-11-13T01:00:01Z"))).isTrue();
	}

}
