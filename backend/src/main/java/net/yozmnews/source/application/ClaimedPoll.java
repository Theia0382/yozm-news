package net.yozmnews.source.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import net.yozmnews.source.FeedId;

/**
 * 점유한 수집 한 회차
 */
public record ClaimedPoll(FeedId feedId, UUID token, Instant scheduledAt) {

	public ClaimedPoll {
		Objects.requireNonNull(feedId, "feedId");
		Objects.requireNonNull(token, "token");
		Objects.requireNonNull(scheduledAt, "scheduledAt");
	}

}
