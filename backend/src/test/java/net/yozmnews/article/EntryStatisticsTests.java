package net.yozmnews.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.source.FeedId;
import org.junit.jupiter.api.Test;

/**
 * 집계 결과 — 피드 하나 · 피드 여럿의 합을 칸별로 낸다. 칸 둘의 결과로 본다.
 * <p>
 * 지키는 규칙: article-R13 · article-R14
 */
class EntryStatisticsTests {

	private static final Instant FROM = Instant.parse("2026-10-30T00:00:00Z");

	private static final Instant MIDDLE = Instant.parse("2026-10-31T00:00:00Z");

	private static final Instant TO = Instant.parse("2026-11-01T00:00:00Z");

	private static final List<EntryBucket> BUCKETS = List.of(new EntryBucket(FROM, MIDDLE), new EntryBucket(MIDDLE, TO));

	private final EntryStatistics statistics = new EntryStatistics(FROM, TO, BUCKETS,
			Map.of(new FeedId(1), List.of(new EntryCounts(10, 1, 1, 0, 2, 0), new EntryCounts(4, 0, 0, 0, 0, 1)),
					new FeedId(2), List.of(new EntryCounts(90, 45, 40, 5, 0, 0), EntryCounts.NONE)));

	@Test
	void aFeedWithEntriesHasItsBucketsAndTheirTotal() {
		FeedEntryStatistics feed = this.statistics.feed(new FeedId(1));

		assertThat(feed.buckets()).containsExactly(new EntryCounts(10, 1, 1, 0, 2, 0), new EntryCounts(4, 0, 0, 0, 0, 1));
		assertThat(feed.total()).isEqualTo(new EntryCounts(14, 1, 1, 0, 2, 1));
	}

	@Test
	void aFeedWithoutEntriesHasEmptyBuckets() {
		FeedEntryStatistics feed = this.statistics.feed(new FeedId(3));

		assertThat(feed.buckets()).containsExactly(EntryCounts.NONE, EntryCounts.NONE);
		assertThat(feed.total()).isEqualTo(EntryCounts.NONE);
	}

	@Test
	void feedsCombineBucketByBucket() {
		FeedEntryStatistics combined = this.statistics.combined(List.of(new FeedId(1), new FeedId(2), new FeedId(3)));

		assertThat(combined.buckets()).containsExactly(new EntryCounts(100, 46, 41, 5, 2, 0), new EntryCounts(4, 0, 0, 0, 0, 1));
		assertThat(combined.total()).isEqualTo(new EntryCounts(104, 46, 41, 5, 2, 1));
		assertThat(this.statistics.combined(List.of()).buckets()).containsExactly(EntryCounts.NONE, EntryCounts.NONE);
	}

	@Test
	void bucketsMustSpanThePeriodWithoutGaps() {
		assertThatIllegalArgumentException().isThrownBy(() -> new EntryStatistics(FROM, TO,
				List.of(new EntryBucket(FROM, MIDDLE), new EntryBucket(MIDDLE.plusSeconds(1), TO)), Map.of()));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new EntryStatistics(FROM, TO, List.of(new EntryBucket(FROM, MIDDLE)), Map.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> new EntryStatistics(FROM, TO, List.of(), Map.of()));
	}

	@Test
	void everyFeedHasOneCountPerBucket() {
		assertThatIllegalArgumentException().isThrownBy(() -> new EntryStatistics(FROM, TO, BUCKETS,
				Map.of(new FeedId(1), List.of(EntryCounts.NONE))));
	}

}
