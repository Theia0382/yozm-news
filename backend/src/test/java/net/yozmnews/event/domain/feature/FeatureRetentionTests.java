package net.yozmnews.event.domain.feature;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * 배정되지 않은 표현의 보관 — 만든 뒤 시간 창 + 여유(1일).
 * <p>
 * 지키는 규칙: event-R13
 */
class FeatureRetentionTests {

	@Test
	void unassignedRepresentationsOlderThanTheWindowAndADayGo() {
		assertThat(FeatureRetention.unassignedBefore(Instant.parse("2026-11-07T00:00:00Z"), Duration.ofHours(72)))
			.isEqualTo(Instant.parse("2026-11-03T00:00:00Z"));
	}

}
