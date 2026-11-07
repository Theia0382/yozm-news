package net.yozmnews.event.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * 기사 하나를 더한 사건 — 중심은 평균, 수 + 1, 첫 · 마지막 기사 시각은 그 발행 시각까지 넓히고, 마지막으로 붙은 시각은 지금이다.
 * <p>
 * 지키는 규칙: event-R12
 */
class LockedEventTests {

	private static final LockedEvent EVENT = new LockedEvent(9, EventStatus.OPEN, "model-a", new float[] { 1, 0 }, 1,
			Instant.parse("2026-11-05T01:00:00Z"), Instant.parse("2026-11-05T02:00:00Z"),
			Instant.parse("2026-11-05T03:00:00Z"), Instant.parse("2026-11-05T03:00:00Z"));

	private static final Instant NOW = Instant.parse("2026-11-06T09:00:00Z");

	@Test
	void joiningAveragesTheCentroidAndCountsTheArticle() {
		LockedEvent joined = EVENT.joined(new float[] { 4, 3 }, Instant.parse("2026-11-05T01:30:00Z"), NOW);

		assertThat(joined.centroid()).containsExactly(2.5f, 1.5f);
		assertThat(joined.articleCount()).isEqualTo(2);
		assertThat(joined.firstArticleAt()).isEqualTo(Instant.parse("2026-11-05T01:00:00Z"));
		assertThat(joined.lastArticleAt()).isEqualTo(Instant.parse("2026-11-05T02:00:00Z"));
		assertThat(joined.id()).isEqualTo(9);
		assertThat(joined.openedAt()).isEqualTo(Instant.parse("2026-11-05T03:00:00Z"));
		assertThat(joined.lastJoinedAt()).isEqualTo(NOW);
	}

	@Test
	void joiningWidensTheSpanBothWays() {
		assertThat(EVENT.joined(new float[] { 1, 0 }, Instant.parse("2026-11-05T00:00:00Z"), NOW).firstArticleAt())
			.isEqualTo(Instant.parse("2026-11-05T00:00:00Z"));
		assertThat(EVENT.joined(new float[] { 1, 0 }, Instant.parse("2026-11-05T03:00:00Z"), NOW).lastArticleAt())
			.isEqualTo(Instant.parse("2026-11-05T03:00:00Z"));
	}

}
