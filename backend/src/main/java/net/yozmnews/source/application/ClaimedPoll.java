package net.yozmnews.source.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import net.yozmnews.source.FeedId;

/**
 * 점유한 수집 한 회차 — 수집 작업을 이 토큰(작업 id)으로 예약 시각에 등록한다.
 *
 * @param feedId 피드
 * @param token 점유 토큰
 * @param scheduledAt 예약 시각
 */
public record ClaimedPoll(FeedId feedId, UUID token, Instant scheduledAt) {

	public ClaimedPoll {
		Objects.requireNonNull(feedId, "feedId");
		Objects.requireNonNull(token, "token");
		Objects.requireNonNull(scheduledAt, "scheduledAt");
	}

}
