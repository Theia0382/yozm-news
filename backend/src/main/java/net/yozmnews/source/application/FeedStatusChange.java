package net.yozmnews.source.application;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.source.domain.poll.FeedPollState;
import net.yozmnews.source.domain.poll.FeedStatus;

/**
 * 피드 건강 검사가 바꾼 피드 하나.
 *
 * @param state 판정한 수집 상태
 * @param at 판정 시각 — 저장한 바뀐 시각
 */
public record FeedStatusChange(FeedPollState state, FeedStatus from, FeedStatus to, Instant at) {

	public FeedStatusChange {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		Objects.requireNonNull(at, "at");
	}

}
