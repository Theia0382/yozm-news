package net.yozmnews.source.application;

import java.util.function.Consumer;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.profile.PollInterval;
import net.yozmnews.source.domain.registry.Activation;
import net.yozmnews.source.domain.registry.Feed;
import net.yozmnews.source.domain.registry.FeedRepository;
import net.yozmnews.source.domain.registry.ProfileResolver;
import net.yozmnews.source.domain.registry.Source;
import net.yozmnews.source.domain.registry.SourceRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 소스 · 피드의 운영 설정 — docs/specs/source.md §4 · source-R4 · source-R5 · source-R14
 */
@Service
class SourceOperations {

	private final SourceRepository sources;

	private final FeedRepository feeds;

	private final ProfileResolver resolver;

	private final TransactionTemplate transaction;

	SourceOperations(SourceRepository sources, FeedRepository feeds, ProfileResolver resolver,
			TransactionTemplate transaction) {
		this.sources = sources;
		this.feeds = feeds;
		this.resolver = resolver;
		this.transaction = transaction;
	}

	/** 소스를 켠다 — source-R4 */
	Activation enableSource(SourceId id) {
		return this.transaction.execute(status -> {
			Source source = source(id);
			Activation activation = source.enable(this.resolver.resolve(source));
			if (activation instanceof Activation.Activated) {
				this.sources.save(source);
			}
			return activation;
		});
	}

	void disableSource(SourceId id) {
		updateSource(id, Source::disable);
	}

	/** 피드를 켠다 — source-R5 */
	Activation enableFeed(FeedId id) {
		return this.transaction.execute(status -> {
			Feed feed = feed(id);
			Activation activation = feed.enable(this.resolver.resolve(source(feed.sourceId())));
			if (activation instanceof Activation.Activated) {
				this.feeds.save(feed);
			}
			return activation;
		});
	}

	void disableFeed(FeedId id) {
		updateFeed(id, Feed::disable);
	}

	/**
	 * 소스의 수집 주기 설정을 바꾼다.
	 * @param minutes 새 설정(분) — null 이면 설정을 지운다
	 * @return 하한보다 짧아(0 · 음수 포함) 바꾸지 않았으면 false
	 */
	boolean changeSourcePollInterval(SourceId id, @Nullable Integer minutes) {
		if (!acceptable(minutes)) {
			return false;
		}
		@Nullable PollInterval interval = toInterval(minutes);
		updateSource(id, source -> source.changePollInterval(interval));
		return true;
	}

	/**
	 * 피드의 수집 주기 설정을 바꾼다.
	 * @param minutes 새 설정(분) — null 이면 설정을 지운다
	 * @return 하한보다 짧아(0 · 음수 포함) 바꾸지 않았으면 false
	 */
	boolean changeFeedPollInterval(FeedId id, @Nullable Integer minutes) {
		if (!acceptable(minutes)) {
			return false;
		}
		@Nullable PollInterval interval = toInterval(minutes);
		updateFeed(id, feed -> feed.changePollInterval(interval));
		return true;
	}

	/** {@link PollInterval#ofMinutes} 가 받는 값인지 본다 — 설정을 지우는 null 도 받는다 */
	private static boolean acceptable(@Nullable Integer minutes) {
		return minutes == null || PollInterval.ofMinutes(minutes).isPresent();
	}

	private static @Nullable PollInterval toInterval(@Nullable Integer minutes) {
		return (minutes != null) ? PollInterval.ofMinutes(minutes).orElseThrow() : null;
	}

	private void updateSource(SourceId id, Consumer<Source> change) {
		this.transaction.executeWithoutResult(status -> {
			Source source = source(id);
			change.accept(source);
			this.sources.save(source);
		});
	}

	private void updateFeed(FeedId id, Consumer<Feed> change) {
		this.transaction.executeWithoutResult(status -> {
			Feed feed = feed(id);
			change.accept(feed);
			this.feeds.save(feed);
		});
	}

	private Source source(SourceId id) {
		return this.sources.findById(id.value())
			.orElseThrow(() -> new IllegalArgumentException("없는 소스: " + id.value()));
	}

	private Feed feed(FeedId id) {
		return this.feeds.findById(id.value()).orElseThrow(() -> new IllegalArgumentException("없는 피드: " + id.value()));
	}

}
