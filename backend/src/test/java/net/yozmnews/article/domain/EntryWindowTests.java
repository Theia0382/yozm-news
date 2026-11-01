package net.yozmnews.article.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.util.List;

import net.yozmnews.article.EntryBucket;
import net.yozmnews.article.EntryCounts;
import net.yozmnews.article.EntryStatistics;
import net.yozmnews.article.domain.EntryStatisticsRepository.EntryCountRow;
import net.yozmnews.source.FeedId;
import org.junit.jupiter.api.Test;

/**
 * 집계 기간과 칸 — 끝에서 거꾸로 7일, 24시간 칸 7개. 칸 경계는 손으로 셈한 시각이다.
 * <p>
 * 지키는 규칙: article-R13
 */
class EntryWindowTests {

	private static final Instant TO = Instant.parse("2026-11-01T03:00:00Z");

	@Test
	void thePeriodIsTheSevenDaysBeforeItsEnd() {
		EntryWindow window = EntryWindow.endingAt(TO);

		assertThat(window.from()).isEqualTo(Instant.parse("2026-10-25T03:00:00Z"));
		assertThat(window.to()).isEqualTo(TO);
	}

	@Test
	void sevenOneDayBucketsSplitThePeriodFromItsStart() {
		List<EntryBucket> buckets = EntryWindow.endingAt(TO).buckets();

		assertThat(buckets).hasSize(7);
		assertThat(buckets.getFirst()).isEqualTo(
				new EntryBucket(Instant.parse("2026-10-25T03:00:00Z"), Instant.parse("2026-10-26T03:00:00Z")));
		assertThat(buckets.get(3)).isEqualTo(
				new EntryBucket(Instant.parse("2026-10-28T03:00:00Z"), Instant.parse("2026-10-29T03:00:00Z")));
		assertThat(buckets.getLast()).isEqualTo(
				new EntryBucket(Instant.parse("2026-10-31T03:00:00Z"), Instant.parse("2026-11-01T03:00:00Z")));
	}

	@Test
	void theEndIsCutToMicroseconds() {
		EntryWindow window = EntryWindow.endingAt(Instant.parse("2026-11-01T03:00:00.123456789Z"));

		assertThat(window.to()).isEqualTo(Instant.parse("2026-11-01T03:00:00.123456Z"));
		assertThat(window.from()).isEqualTo(Instant.parse("2026-10-25T03:00:00.123456Z"));
	}

	@Test
	void rowsFillTheirBucketsAndTheRestStayEmpty() {
		EntryStatistics statistics = EntryWindow.endingAt(TO)
			.statistics(List.of(new EntryCountRow(new FeedId(9), 0, new EntryCounts(2, 1, 0, 0, 0)),
					new EntryCountRow(new FeedId(9), 6, new EntryCounts(3, 0, 0, 3, 1))));

		assertThat(statistics.from()).isEqualTo(Instant.parse("2026-10-25T03:00:00Z"));
		assertThat(statistics.to()).isEqualTo(TO);
		assertThat(statistics.countsByFeed()).containsOnlyKeys(new FeedId(9));
		assertThat(statistics.feed(new FeedId(9)).buckets()).containsExactly(new EntryCounts(2, 1, 0, 0, 0),
				EntryCounts.NONE, EntryCounts.NONE, EntryCounts.NONE, EntryCounts.NONE, EntryCounts.NONE,
				new EntryCounts(3, 0, 0, 3, 1));
	}

	@Test
	void noRowsMeansNoFeed() {
		assertThat(EntryWindow.endingAt(TO).statistics(List.of()).countsByFeed()).isEmpty();
	}

	@Test
	void aBucketOutsideThePeriodOrTwiceIsAMismatch() {
		EntryWindow window = EntryWindow.endingAt(TO);
		EntryCounts one = new EntryCounts(1, 0, 0, 0, 0);

		assertThatIllegalStateException()
			.isThrownBy(() -> window.statistics(List.of(new EntryCountRow(new FeedId(9), 7, one))));
		assertThatIllegalStateException()
			.isThrownBy(() -> window.statistics(List.of(new EntryCountRow(new FeedId(9), -1, one))));
		assertThatIllegalStateException().isThrownBy(() -> window.statistics(
				List.of(new EntryCountRow(new FeedId(9), 2, one), new EntryCountRow(new FeedId(9), 2, one))));
		// 두 번째 판정은 개수가 아니라 자리로 한다 — 먼저 온 행이 빈 개수여도 같은 칸이다
		assertThatIllegalStateException().isThrownBy(() -> window.statistics(List
			.of(new EntryCountRow(new FeedId(9), 2, EntryCounts.NONE), new EntryCountRow(new FeedId(9), 2, one))));
	}

}
