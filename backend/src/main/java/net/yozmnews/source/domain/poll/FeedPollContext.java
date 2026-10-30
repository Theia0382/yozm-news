package net.yozmnews.source.domain.poll;

import java.util.Objects;

import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.profile.FeedKey;
import net.yozmnews.source.domain.profile.PollIntervalSettings;

/**
 * 수집할 피드 — 수집 상태와, 수집에 필요한 그 피드 · 소스의 운영 설정.
 * <p>
 * 대상 조회와 점유 확인이 함께 쓴다.
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
