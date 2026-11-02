package net.yozmnews.source;

import java.util.Objects;

/**
 * 운영자가 피드를 지웠다(source → article) — docs/specs/source.md §3.
 * <p>
 * 피드 · 그 수집 상태 행을 지운 트랜잭션에서 발행한다. 소비자는 그 피드의 기록을 지운다.
 */
public record FeedDeleted(SourceId sourceId, FeedId feedId) {

	public FeedDeleted {
		Objects.requireNonNull(sourceId, "sourceId");
		Objects.requireNonNull(feedId, "feedId");
	}

}
