package net.yozmnews.source.application;

import java.time.Instant;

import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.poll.FeedPollState;
import net.yozmnews.source.domain.poll.FeedPollStateRepository;
import net.yozmnews.source.domain.profile.FeedKey;
import net.yozmnews.source.domain.registry.Feed;
import net.yozmnews.source.domain.registry.FeedRepository;
import org.springframework.stereotype.Component;

/**
 * 피드 행과 그 수집 상태 행을 함께 만든다 — 등록과 기동 대조가 같은 경로를 쓴다. 이미 있으면 아무것도 하지 않으므로, 여러 인스턴스가
 * 동시에 불러도 행은 하나다. 호출자의 트랜잭션 안에서 부른다.
 */
@Component
class FeedProvisioner {

	private final FeedRepository feeds;

	private final FeedPollStateRepository pollStates;

	FeedProvisioner(FeedRepository feeds, FeedPollStateRepository pollStates) {
		this.feeds = feeds;
		this.pollStates = pollStates;
	}

	/** @return 그 피드 — 새로 만들었든 이미 있었든 */
	Feed provision(SourceId source, FeedKey key, Instant now) {
		this.feeds.insertIfAbsent(source.value(), key.value(), now);
		Feed feed = this.feeds.findBySourceIdAndFeedKey(source.value(), key.value()).orElseThrow();
		this.pollStates.insertIfAbsent(FeedPollState.initial(feed.id(), now));
		return feed;
	}

}
