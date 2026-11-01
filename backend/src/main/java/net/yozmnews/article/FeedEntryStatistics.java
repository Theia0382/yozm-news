package net.yozmnews.article;

import java.util.List;

/**
 * 피드 하나 또는 피드 여럿의 합의 칸별 엔트리 개수 — docs/specs/article.md §3 · article-R13.
 *
 * @param buckets 칸마다의 개수 — {@link EntryStatistics#buckets()} 와 같은 순서 · 같은 길이
 */
public record FeedEntryStatistics(List<EntryCounts> buckets) {

	public FeedEntryStatistics {
		buckets = List.copyOf(buckets);
	}

	/** 기간 전체 — 칸이 기간을 빈틈없이 나누므로 칸을 모두 더한 값이다 */
	public EntryCounts total() {
		return this.buckets.stream().reduce(EntryCounts.NONE, EntryCounts::plus);
	}

}
