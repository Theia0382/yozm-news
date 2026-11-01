package net.yozmnews.article;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.source.FeedId;

/**
 * 엔트리 집계 결과 — 기간, 칸, 엔트리가 있는 피드의 칸별 개수. docs/specs/article.md §3 · article-R13.
 *
 * @param from 기간의 시작(포함)
 * @param to 기간의 끝(제외) — 집계한 시각
 * @param buckets 기간을 빈틈없이 나눈 칸 — 시각 순
 * @param countsByFeed 기간 안에 엔트리가 있는 피드의 칸별 개수 — 값마다 칸과 같은 길이 · 같은 순서
 */
public record EntryStatistics(Instant from, Instant to, List<EntryBucket> buckets,
		Map<FeedId, List<EntryCounts>> countsByFeed) {

	public EntryStatistics {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		buckets = List.copyOf(buckets);
		if (buckets.isEmpty() || !buckets.getFirst().from().equals(from) || !buckets.getLast().to().equals(to)) {
			throw new IllegalArgumentException("칸이 기간의 시작과 끝을 잇지 않는다: " + from + " ~ " + to);
		}
		for (int i = 1; i < buckets.size(); i++) {
			if (!buckets.get(i - 1).to().equals(buckets.get(i).from())) {
				throw new IllegalArgumentException("칸 사이에 빈틈이 있다: " + buckets.get(i - 1) + " · " + buckets.get(i));
			}
		}
		Map<FeedId, List<EntryCounts>> copies = new HashMap<>();
		for (Map.Entry<FeedId, List<EntryCounts>> entry : countsByFeed.entrySet()) {
			if (entry.getValue().size() != buckets.size()) {
				throw new IllegalArgumentException("피드 " + entry.getKey().value() + " 의 칸 수가 " + entry.getValue().size()
						+ " 이다 — " + buckets.size() + " 이어야 한다");
			}
			copies.put(entry.getKey(), List.copyOf(entry.getValue()));
		}
		countsByFeed = Map.copyOf(copies);
	}

	/** 피드 하나의 칸별 개수 — 기간 안에 엔트리가 없으면 모든 칸이 {@link EntryCounts#NONE} 이다 */
	public FeedEntryStatistics feed(FeedId feed) {
		List<EntryCounts> counts = this.countsByFeed.get(feed);
		return new FeedEntryStatistics(
				(counts != null) ? counts : Collections.nCopies(this.buckets.size(), EntryCounts.NONE));
	}

	/** 피드 여럿의 칸별 합 — 같은 칸끼리 개수를 더한다 */
	public FeedEntryStatistics combined(Collection<FeedId> feeds) {
		List<EntryCounts> sums = new ArrayList<>(Collections.nCopies(this.buckets.size(), EntryCounts.NONE));
		for (FeedId feed : feeds) {
			List<EntryCounts> counts = feed(feed).buckets();
			for (int i = 0; i < sums.size(); i++) {
				sums.set(i, sums.get(i).plus(counts.get(i)));
			}
		}
		return new FeedEntryStatistics(sums);
	}

}
