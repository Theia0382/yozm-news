package net.yozmnews.source.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.yozmnews.source.FeedDeleted;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedRemovalRefusal;
import net.yozmnews.source.SourceDeleted;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.UnknownFeedException;
import net.yozmnews.source.UnknownSourceException;
import net.yozmnews.source.domain.attribution.SourceAttributionRepository;
import net.yozmnews.source.domain.poll.FeedPollStateRepository;
import net.yozmnews.source.domain.poll.SourceFetchSlotRepository;
import net.yozmnews.source.domain.registry.Feed;
import net.yozmnews.source.domain.registry.FeedRemovalRule;
import net.yozmnews.source.domain.registry.FeedRepository;
import net.yozmnews.source.domain.registry.ProfileResolver;
import net.yozmnews.source.domain.registry.Source;
import net.yozmnews.source.domain.registry.SourceRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 소스 · 피드의 숨김과 삭제 — docs/specs/source.md §4.
 * <p>
 * 숨김은 끄기를 포함하고 행 · 원문 · 받은 기사를 남긴다. 소스 · 피드 행의 버전 조건이 겹친 운영 변경을 가른다. 삭제는 소스 행을 배타
 * 잠금한 뒤 그 행들을 자식부터 지우고, 같은 트랜잭션에서 삭제 이벤트를 발행한다 — 기사 쪽은 소비자가 지운다. 연산마다 한 트랜잭션이고,
 * 운영자의 숨기기 · 다시 보이기 · 삭제는 커밋 뒤 경고 로그로 남긴다.
 */
@Service
class SourceRemoval {

	private static final Logger logger = LoggerFactory.getLogger(SourceRemoval.class);

	private final SourceRepository sources;

	private final FeedRepository feeds;

	private final ProfileResolver resolver;

	private final FeedPollStateRepository pollStates;

	private final SourceFetchSlotRepository fetchSlots;

	private final SourceAttributionRepository attributions;

	private final ApplicationEventPublisher events;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	SourceRemoval(SourceRepository sources, FeedRepository feeds, ProfileResolver resolver,
			FeedPollStateRepository pollStates, SourceFetchSlotRepository fetchSlots,
			SourceAttributionRepository attributions, ApplicationEventPublisher events, TransactionTemplate transaction) {
		this(sources, feeds, resolver, pollStates, fetchSlots, attributions, events, transaction, InstantSource.system());
	}

	SourceRemoval(SourceRepository sources, FeedRepository feeds, ProfileResolver resolver,
			FeedPollStateRepository pollStates, SourceFetchSlotRepository fetchSlots,
			SourceAttributionRepository attributions, ApplicationEventPublisher events, TransactionTemplate transaction,
			InstantSource clock) {
		this.sources = sources;
		this.feeds = feeds;
		this.resolver = resolver;
		this.pollStates = pollStates;
		this.fetchSlots = fetchSlots;
		this.attributions = attributions;
		this.events = events;
		this.transaction = transaction;
		this.clock = clock;
	}

	/**
	 * 소스를 숨긴다 — 소스와 그 피드를 모두 끈다. 이미 숨긴 소스면 아무것도 바꾸지 않는다.
	 * @throws UnknownSourceException 없는 소스일 때
	 */
	void hideSource(SourceId id) {
		Instant now = this.clock.instant();
		Source hidden = this.transaction.execute(status -> {
			Source source = this.sources.findById(id.value()).orElseThrow(() -> new UnknownSourceException(id));
			if (source.hidden()) {
				return null;
			}
			source.hide(now);
			this.sources.save(source);
			// 소스 행 뒤에 피드를 읽어, 먼저 커밋된 원문 수정이 더한 피드까지 끈다
			for (Feed feed : this.feeds.findAllBySourceId(id.value())) {
				feed.disable();
				this.feeds.save(feed);
			}
			return source;
		});
		if (hidden != null) {
			logger.warn("운영자가 소스를 숨겼다 — 소스 {} 프리셋 {}", id.value(), presetKey(hidden));
		}
	}

	/**
	 * 숨긴 소스를 다시 보인다 — 소스 · 피드는 꺼진 채다. 숨기지 않은 소스면 아무것도 바꾸지 않는다.
	 * @throws UnknownSourceException 없는 소스일 때
	 */
	void unhideSource(SourceId id) {
		Source shown = this.transaction.execute(status -> {
			Source source = this.sources.findById(id.value()).orElseThrow(() -> new UnknownSourceException(id));
			if (!source.hidden()) {
				return null;
			}
			source.unhide();
			this.sources.save(source);
			return source;
		});
		if (shown != null) {
			logger.warn("운영자가 숨긴 소스를 다시 보였다 — 소스 {} 프리셋 {}", id.value(), presetKey(shown));
		}
	}

	/**
	 * 피드를 숨긴다 — 프로필을 얻었고 그 프로필에 없는 피드만. 이미 숨긴 피드면 아무것도 바꾸지 않는다.
	 * @return 숨기지 않은 이유 — 숨겼거나 이미 숨겼으면 비어 있다
	 * @throws UnknownFeedException 없는 피드 · 숨긴 소스의 피드일 때
	 */
	Optional<FeedRemovalRefusal> hideFeed(FeedId id) {
		Instant now = this.clock.instant();
		FeedHiding hiding = this.transaction.execute(status -> {
			Feed feed = this.feeds.findById(id.value()).orElseThrow(() -> new UnknownFeedException(id));
			Source source = visibleSourceOf(feed, id);
			if (feed.hidden()) {
				return new FeedHiding.Unchanged();
			}
			Optional<FeedRemovalRefusal> refusal = FeedRemovalRule.refusal(this.resolver.resolve(source), feed.feedKey());
			if (refusal.isPresent()) {
				return new FeedHiding.Refused(refusal.get());
			}
			feed.hide(now);
			this.feeds.save(feed);
			return new FeedHiding.Hidden(source);
		});
		return switch (hiding) {
			case FeedHiding.Unchanged unchanged -> Optional.empty();
			case FeedHiding.Refused refused -> Optional.of(refused.reason());
			case FeedHiding.Hidden hidden -> {
				logger.warn("운영자가 피드를 숨겼다 — 소스 {} 프리셋 {} 피드 {}", hidden.source().id().value(),
						presetKey(hidden.source()), id.value());
				yield Optional.empty();
			}
		};
	}

	/**
	 * 숨긴 피드를 다시 보인다 — 꺼진 채다. 숨기지 않은 피드면 아무것도 바꾸지 않는다.
	 * @throws UnknownFeedException 없는 피드 · 숨긴 소스의 피드일 때
	 */
	void unhideFeed(FeedId id) {
		Source shown = this.transaction.execute(status -> {
			Feed feed = this.feeds.findById(id.value()).orElseThrow(() -> new UnknownFeedException(id));
			Source source = visibleSourceOf(feed, id);
			if (!feed.hidden()) {
				return null;
			}
			feed.unhide();
			this.feeds.save(feed);
			return source;
		});
		if (shown != null) {
			logger.warn("운영자가 숨긴 피드를 다시 보였다 — 소스 {} 프리셋 {} 피드 {}", shown.id().value(), presetKey(shown), id.value());
		}
	}

	/**
	 * 소스를 지운다 — 수집 상태 · 피드 · 요청 예약 · 출처 이름 · 소스 행을 지우고 {@link SourceDeleted} 를 발행한다. 숨긴 소스도 지운다.
	 * 켜진 소스도 지운다.
	 * @throws UnknownSourceException 없는 소스일 때
	 */
	void deleteSource(SourceId id) {
		SourceDeletion deletion = this.transaction.execute(status -> {
			// 소스 행을 먼저 잠근다 — 같은 소스의 운영 변경 · 요청 예약 · 기동 대조와 줄을 서고, 그 뒤에 피드를 읽는다
			if (this.sources.lockForUpdate(id.value()).isEmpty()) {
				throw new UnknownSourceException(id);
			}
			Source source = this.sources.findById(id.value()).orElseThrow(() -> new UnknownSourceException(id));
			List<FeedId> feedIds = this.feeds.findAllBySourceId(id.value())
				.stream()
				.map(Feed::id)
				.sorted(Comparator.comparingLong(FeedId::value))
				.toList();
			this.pollStates.lockForRemoval(feedIds);
			this.pollStates.delete(feedIds);
			this.feeds.deleteAllById(feedIds.stream().map(FeedId::value).toList());
			this.fetchSlots.deleteBySource(id);
			this.attributions.delete(id);
			this.sources.deleteById(id.value());
			this.events.publishEvent(new SourceDeleted(id));
			return new SourceDeletion(source, feedIds);
		});
		logger.warn("운영자가 소스를 지웠다 — 소스 {} 프리셋 {} 피드 {}", id.value(), presetKey(deletion.source()),
				deletion.feeds().stream().map(FeedId::value).toList());
	}

	/**
	 * 피드를 지운다 — 프로필을 얻었고 그 프로필에 없는 피드만. 수집 상태 · 피드 행을 지우고 {@link FeedDeleted} 를 발행한다. 숨긴 피드 ·
	 * 켜진 피드도 지운다.
	 * @return 지우지 않은 이유 — 지웠으면 비어 있다
	 * @throws UnknownFeedException 없는 피드 · 숨긴 소스의 피드일 때
	 */
	Optional<FeedRemovalRefusal> deleteFeed(FeedId id) {
		FeedRemoval removal = this.transaction.execute(status -> {
			Feed found = this.feeds.findById(id.value()).orElseThrow(() -> new UnknownFeedException(id));
			// 소스 행을 먼저 잠근다 — 원문 수정이 같은 식별자를 다시 넣는 것과 줄을 서고, 그 뒤에 다시 읽어 판정한다
			if (this.sources.lockForUpdate(found.sourceId().value()).isEmpty()) {
				throw new UnknownFeedException(id);
			}
			Source source = visibleSourceOf(found, id);
			Feed feed = this.feeds.findById(id.value()).orElseThrow(() -> new UnknownFeedException(id));
			Optional<FeedRemovalRefusal> refusal = FeedRemovalRule.refusal(this.resolver.resolve(source), feed.feedKey());
			if (refusal.isPresent()) {
				return new FeedRemoval.Refused(refusal.get());
			}
			this.pollStates.lockForRemoval(List.of(id));
			this.pollStates.delete(List.of(id));
			this.feeds.deleteById(id.value());
			this.events.publishEvent(new FeedDeleted(source.id(), id));
			return new FeedRemoval.Deleted(source);
		});
		return switch (removal) {
			case FeedRemoval.Refused refused -> Optional.of(refused.reason());
			case FeedRemoval.Deleted deleted -> {
				logger.warn("운영자가 피드를 지웠다 — 소스 {} 프리셋 {} 피드 {}", deleted.source().id().value(),
						presetKey(deleted.source()), id.value());
				yield Optional.empty();
			}
		};
	}

	/** 숨긴 소스의 피드는 피드 단위 연산의 대상이 아니다 — 소스 단위로 다시 보이거나 지운다 */
	private Source visibleSourceOf(Feed feed, FeedId id) {
		return this.sources.findById(feed.sourceId().value())
			.filter(source -> !source.hidden())
			.orElseThrow(() -> new UnknownFeedException(id));
	}

	private static @Nullable String presetKey(Source source) {
		return source.presetKey().map(key -> key.value()).orElse(null);
	}

	/** 소스 삭제 트랜잭션의 결과 — 로그는 커밋 뒤에 남긴다 */
	private record SourceDeletion(Source source, List<FeedId> feeds) {
	}

	/** 피드 삭제 트랜잭션의 결과 — 로그는 커밋 뒤에 남긴다 */
	private sealed interface FeedRemoval {

		record Refused(FeedRemovalRefusal reason) implements FeedRemoval {
		}

		record Deleted(Source source) implements FeedRemoval {
		}

	}

	/** 피드 숨기기 트랜잭션의 결과 — 로그는 커밋 뒤에 남긴다 */
	private sealed interface FeedHiding {

		record Unchanged() implements FeedHiding {
		}

		record Refused(FeedRemovalRefusal reason) implements FeedHiding {
		}

		record Hidden(Source source) implements FeedHiding {
		}

	}

}
