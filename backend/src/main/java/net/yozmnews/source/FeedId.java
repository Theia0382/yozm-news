package net.yozmnews.source;

/**
 * 피드 식별자 — docs/specs/source.md §3
 */
public record FeedId(long value) {

	public FeedId {
		if (value < 1) {
			throw new IllegalArgumentException("피드 식별자는 1 이상이어야 한다: " + value);
		}
	}

}
