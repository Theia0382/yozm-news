package net.yozmnews.source;

import java.util.Objects;

/**
 * 피드 하나의 상태 전이 — {@link FeedStatusesChanged} 의 원소. docs/specs/source.md §3
 *
 * @param feedId 피드
 * @param from 이전 상태
 * @param to 새 상태 — 이전 상태와 다르다
 */
public record FeedStatusTransition(FeedId feedId, FeedStatus from, FeedStatus to) {

	public FeedStatusTransition {
		Objects.requireNonNull(feedId, "feedId");
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		if (from == to) {
			throw new IllegalArgumentException("상태가 바뀌지 않았다: " + from);
		}
	}

}
