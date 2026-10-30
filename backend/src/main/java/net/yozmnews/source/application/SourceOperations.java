package net.yozmnews.source.application;

import java.time.InstantSource;
import java.util.function.Consumer;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.fetch.ArticleSource;
import net.yozmnews.source.domain.fetch.FeedDescriptor;
import net.yozmnews.source.domain.fetch.FetchState;
import net.yozmnews.source.domain.gate.FeedActivation;
import net.yozmnews.source.domain.gate.FeedGate;
import net.yozmnews.source.domain.gate.PreviewOutcome;
import net.yozmnews.source.domain.poll.PollSchedule;
import net.yozmnews.source.domain.poll.PollSchedule.FetchSlot;
import net.yozmnews.source.domain.profile.PollInterval;
import net.yozmnews.source.domain.registry.Activation;
import net.yozmnews.source.domain.registry.Feed;
import net.yozmnews.source.domain.registry.FeedRepository;
import net.yozmnews.source.domain.registry.ProfileResolver;
import net.yozmnews.source.domain.registry.Source;
import net.yozmnews.source.domain.registry.SourceRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 소스 · 피드의 운영 설정 — docs/specs/source.md §4 · source-R4 · source-R5 · source-R9 · source-R14 · source-R18.
 * <p>
 * 피드 켜기는 피드를 받으므로 호출자의 트랜잭션 밖에서 부른다 — 안에서 부르면 대기와 요청이 그 트랜잭션 안에 든다.
 */
@Service
class SourceOperations {

	private final SourceRepository sources;

	private final FeedRepository feeds;

	private final ProfileResolver resolver;

	private final ArticleSource articleSource;

	private final SourceFetchSlots fetchSlots;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	SourceOperations(SourceRepository sources, FeedRepository feeds, ProfileResolver resolver,
			ArticleSource articleSource, SourceFetchSlots fetchSlots, TransactionTemplate transaction) {
		this(sources, feeds, resolver, articleSource, fetchSlots, transaction, InstantSource.system());
	}

	SourceOperations(SourceRepository sources, FeedRepository feeds, ProfileResolver resolver,
			ArticleSource articleSource, SourceFetchSlots fetchSlots, TransactionTemplate transaction,
			InstantSource clock) {
		this.sources = sources;
		this.feeds = feeds;
		this.resolver = resolver;
		this.articleSource = articleSource;
		this.fetchSlots = fetchSlots;
		this.transaction = transaction;
		this.clock = clock;
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

	/**
	 * 피드를 켠다 — source-R5 · source-R9 · source-R18.
	 * @throws IllegalStateException 요청 순서를 기다리다 중단됐을 때 — 요청 시작 시각 예약만 남는다
	 */
	FeedActivation enableFeed(FeedId id) {
		FeedGate.Precheck precheck = this.transaction.execute(status -> {
			Feed feed = feed(id);
			return FeedGate.precheck(feed, this.resolver.resolve(source(feed.sourceId())));
		});
		return switch (precheck) {
			case FeedGate.Precheck.Decided decided -> decided.activation();
			case FeedGate.Precheck.Fetch fetch -> previewAndEnable(id, fetch);
		};
	}

	private FeedActivation previewAndEnable(FeedId id, FeedGate.Precheck.Fetch fetch) {
		FetchSlot slot = this.fetchSlots.reserve(fetch.source(), this.clock);
		if (!SourceFetchSlots.sleep(PollSchedule.remaining(slot.start(), this.clock.instant()))) {
			throw new IllegalStateException("같은 소스의 요청 순서를 기다리다 중단됐다 — 피드 " + id.value());
		}
		FeedDescriptor descriptor = fetch.descriptor();
		PreviewOutcome outcome = FeedGate.preview(this.articleSource.fetch(descriptor, FetchState.NONE),
				descriptor.profile());
		return this.transaction.execute(status -> {
			Feed feed = feed(id);
			FeedActivation activation = FeedGate.enable(feed, this.resolver.resolve(source(feed.sourceId())),
					descriptor.url(), outcome);
			if (activation instanceof FeedActivation.Activated) {
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
