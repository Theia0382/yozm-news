package net.yozmnews.article.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.article.EntryBucket;
import net.yozmnews.article.EntryCounts;
import net.yozmnews.article.EntryStatistics;
import net.yozmnews.article.domain.EntryStatisticsRepository.EntryCountRow;
import net.yozmnews.source.FeedId;
import org.jspecify.annotations.Nullable;

/**
 * 엔트리 집계 기간과 칸 — 기간 · 칸 길이의 정본. docs/specs/article.md §4 · article-R13.
 * <p>
 * 기간은 끝 시각에서 거꾸로 잡아 시간대가 없다. 칸은 기간을 시작부터 같은 길이로 빈틈없이 나눈다 — 시작 포함, 끝 제외.
 */
public final class EntryWindow {

	/** 집계 기간의 길이 */
	public static final Duration LENGTH = Duration.ofDays(7);

	/** 칸 하나의 길이 — 기간 길이를 나누어떨어지게 한다 */
	public static final Duration BUCKET = Duration.ofDays(1);

	private static final int BUCKET_COUNT = bucketCount();

	private final Instant from;

	private final Instant to;

	private EntryWindow(Instant to) {
		this.to = to;
		this.from = to.minus(LENGTH);
	}

	/**
	 * 끝 시각에서 거꾸로 잡은 기간 — 끝은 마이크로초로 자른다. 저장된 시각(timestamptz)의 정밀도라 질의의 경계와 칸 경계가 같은 값이
	 * 된다.
	 */
	public static EntryWindow endingAt(Instant to) {
		Objects.requireNonNull(to, "to");
		return new EntryWindow(to.truncatedTo(ChronoUnit.MICROS));
	}

	public Instant from() {
		return this.from;
	}

	public Instant to() {
		return this.to;
	}

	/** 칸 — 시각 순, i 번째는 [시작 + i · 칸 길이, 시작 + (i + 1) · 칸 길이) */
	public List<EntryBucket> buckets() {
		List<EntryBucket> buckets = new ArrayList<>(BUCKET_COUNT);
		for (int i = 0; i < BUCKET_COUNT; i++) {
			buckets.add(new EntryBucket(this.from.plus(BUCKET.multipliedBy(i)), this.from.plus(BUCKET.multipliedBy(i + 1))));
		}
		return buckets;
	}

	/**
	 * 피드 · 칸마다 센 행을 집계 결과로 모은다 — 행이 없는 칸은 {@link EntryCounts#NONE} 이다.
	 * @throws IllegalStateException 칸 번호가 범위 밖이거나 같은 (피드, 칸)이 두 번 있을 때 — 질의와 칸 계산이 어긋난 것이다
	 */
	public EntryStatistics statistics(List<EntryCountRow> rows) {
		Map<FeedId, @Nullable EntryCounts[]> filled = new HashMap<>();
		for (EntryCountRow row : rows) {
			if (row.bucket() < 0 || row.bucket() >= BUCKET_COUNT) {
				throw new IllegalStateException("칸 번호가 범위 밖이다: 피드 " + row.feedId().value() + " · 칸 " + row.bucket());
			}
			@Nullable EntryCounts[] counts = filled.computeIfAbsent(row.feedId(), feed -> new EntryCounts[BUCKET_COUNT]);
			if (counts[row.bucket()] != null) {
				throw new IllegalStateException("같은 칸이 두 번 있다: 피드 " + row.feedId().value() + " · 칸 " + row.bucket());
			}
			counts[row.bucket()] = row.counts();
		}
		Map<FeedId, List<EntryCounts>> countsByFeed = new HashMap<>();
		filled.forEach((feed, counts) -> countsByFeed.put(feed,
				Arrays.stream(counts).map(count -> (count != null) ? count : EntryCounts.NONE).toList()));
		return new EntryStatistics(this.from, this.to, buckets(), countsByFeed);
	}

	private static int bucketCount() {
		if (!BUCKET.isPositive() || !LENGTH.isPositive() || LENGTH.toNanos() % BUCKET.toNanos() != 0) {
			throw new IllegalStateException("칸 길이가 기간 길이를 나누어떨어지게 하지 않는다: " + LENGTH + " / " + BUCKET);
		}
		return Math.toIntExact(LENGTH.dividedBy(BUCKET));
	}

}
