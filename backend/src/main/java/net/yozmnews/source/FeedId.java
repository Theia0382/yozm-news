package net.yozmnews.source;

/**
 * 피드 식별자 — {@code feeds.id}. 수집 프로필 안의 피드 식별자(feed key)와 다르다.
 *
 * @param value 1 이상
 */
public record FeedId(long value) {

	public FeedId {
		if (value < 1) {
			throw new IllegalArgumentException("피드 식별자는 1 이상이어야 한다: " + value);
		}
	}

}
