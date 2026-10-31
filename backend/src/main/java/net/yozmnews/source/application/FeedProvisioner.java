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
 * 피드 행과 수집 상태 행 만들기 — docs/specs/source.md §4 · source-R3 · source-R8
 */
@Component
class FeedProvisioner {

	private final FeedRepository feeds;

	private final FeedPollStateRepository pollStates;

	FeedProvisioner(FeedRepository feeds, FeedPollStateRepository pollStates) {
		this.feeds = feeds;
		this.pollStates = pollStates;
	}

	/**
	 * 피드 행과 수집 상태 행을 없으면 만든다.
	 */
	void provision(SourceId source, FeedKey key, Instant now) {
		this.feeds.insertIfAbsent(source.value(), key.value(), now);
		Feed feed = this.feeds.findBySourceIdAndFeedKey(source.value(), key.value()).orElseThrow();
		this.pollStates.insertIfAbsent(FeedPollState.initial(feed.id(), now));
	}

}
