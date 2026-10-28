package net.yozmnews.source.domain.poll;

import java.util.Optional;

import net.yozmnews.source.FeedId;

/**
 * 수집 상태 저장소 — 수집 작업만 쓴다. 전이는 {@link FeedPollState} 가 계산하고, 저장소는 그 값을 옮긴다.
 */
public interface FeedPollStateRepository {

	/** 피드의 수집 상태 행을 만든다 — 이미 있으면 아무것도 하지 않는다 */
	void insertIfAbsent(FeedPollState initial);

	Optional<FeedPollState> find(FeedId feedId);

}
