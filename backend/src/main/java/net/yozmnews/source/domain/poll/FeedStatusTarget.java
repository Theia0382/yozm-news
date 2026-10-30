package net.yozmnews.source.domain.poll;

import java.util.Objects;

/** 판정할 피드 — 수집 상태와 저장된 피드 상태 */
public record FeedStatusTarget(FeedPollState state, FeedStatus status) {

	public FeedStatusTarget {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(status, "status");
	}

}
