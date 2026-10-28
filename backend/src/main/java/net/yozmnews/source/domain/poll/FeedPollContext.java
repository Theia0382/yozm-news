package net.yozmnews.source.domain.poll;

import java.util.Objects;

import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.profile.FeedKey;
import net.yozmnews.source.domain.profile.PollIntervalSettings;

/**
 * 수집할 피드 — 수집 상태와, 수집에 필요한 그 피드 · 소스의 운영 설정. 대상 조회와 점유 확인이 함께 쓴다.
 *
 * @param state 수집 상태
 * @param sourceId 피드가 속한 소스
 * @param feedKey 프로필 안의 피드 식별자
 * @param operator 운영자 주기 설정(소스 · 피드)
 */
public record FeedPollContext(FeedPollState state, SourceId sourceId, FeedKey feedKey,
		PollIntervalSettings operator) {

	public FeedPollContext {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(sourceId, "sourceId");
		Objects.requireNonNull(feedKey, "feedKey");
		Objects.requireNonNull(operator, "operator");
	}

}
