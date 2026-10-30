package net.yozmnews.source.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import net.yozmnews.source.ArticleFetched;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FetchedArticle;
import net.yozmnews.source.domain.fetch.ArticleSource;
import net.yozmnews.source.domain.fetch.FetchResult;
import net.yozmnews.source.domain.poll.FeedPollContext;
import net.yozmnews.source.domain.poll.FeedPollState;
import net.yozmnews.source.domain.poll.FeedPollStateRepository;
import net.yozmnews.source.domain.poll.Lease;
import net.yozmnews.source.domain.poll.PollOutcome;
import net.yozmnews.source.domain.poll.PollSchedule;
import net.yozmnews.source.domain.poll.PollSchedule.FetchSlot;
import net.yozmnews.source.domain.profile.PollInterval;
import net.yozmnews.source.domain.registry.PollTarget;
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
 * 점유한 피드의 수집 한 회차 — docs/specs/source.md §4 · source-R16 ~ source-R21.
 * <p>
 * 작업 서버는 예약 작업을 poll 간격만큼 일찍 시작할 수 있어, 예약 시각까지 기다린 뒤 수집한다. 기다리다 중단되면 수집하지 않고 점유가
 * 만료되기를 둔다.
 */
@Service
public class FeedPoller {

	private static final Logger logger = LoggerFactory.getLogger(FeedPoller.class);

	private final FeedPollStateRepository pollStates;

	private final SourceFetchSlots fetchSlots;

	private final SourceRepository sources;

	private final ProfileResolver resolver;

	private final ArticleSource articleSource;

	private final ApplicationEventPublisher events;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	FeedPoller(FeedPollStateRepository pollStates, SourceFetchSlots fetchSlots, SourceRepository sources,
			ProfileResolver resolver, ArticleSource articleSource, ApplicationEventPublisher events,
			TransactionTemplate transaction) {
		this(pollStates, fetchSlots, sources, resolver, articleSource, events, transaction, InstantSource.system());
	}

	FeedPoller(FeedPollStateRepository pollStates, SourceFetchSlots fetchSlots, SourceRepository sources,
			ProfileResolver resolver, ArticleSource articleSource, ApplicationEventPublisher events,
			TransactionTemplate transaction, InstantSource clock) {
		this.pollStates = pollStates;
		this.fetchSlots = fetchSlots;
		this.sources = sources;
		this.resolver = resolver;
		this.articleSource = articleSource;
		this.events = events;
		this.transaction = transaction;
		this.clock = clock;
	}

	public void poll(FeedId feedId, UUID token) {
		Optional<FeedPollContext> leased = this.pollStates.findLeased(feedId, token);
		if (leased.isEmpty()) {
			logger.debug("점유가 없어 수집하지 않는다 — 피드 {} 토큰 {}", feedId.value(), token);
			return;
		}
		FeedPollContext context = leased.get();
		Source source = this.sources.findById(context.sourceId().value()).orElseThrow();
		switch (PollTarget.of(this.resolver.resolve(source), context.feedKey())) {
			case PollTarget.Ready target -> fetch(context, token, target);
			case PollTarget.Unavailable unavailable -> {
				FeedPollState deferred = context.state().deferForUnavailableProfile(this.clock.instant());
				this.transaction.executeWithoutResult(status -> this.pollStates.replaceIfLeased(token, deferred));
				logger.warn("프로필에서 피드를 얻지 못해 수집을 미룬다 — 소스 {} 피드 {}({}): {}", context.sourceId().value(),
						feedId.value(), context.feedKey(), unavailable.reason());
			}
		}
	}

	private void fetch(FeedPollContext context, UUID token, PollTarget.Ready target) {
		FeedPollState state = context.state();
		Lease lease = Objects.requireNonNull(state.lease(), "lease");
		if (!SourceFetchSlots.sleep(PollSchedule.waitBeforeFetch(lease, this.clock.instant()))) {
			return;
		}
		FetchSlot slot = this.fetchSlots.reserve(context.sourceId(), this.clock);
		if (!PollSchedule.fitsLease(slot, state.nextPollAt())) {
			logger.warn("같은 소스의 요청 예약이 점유 만료를 넘는다 — 피드 {} 를 이번에 수집하지 않는다(시작 {}, 만료 {})",
					state.feedId().value(), slot.start(), state.nextPollAt());
			return;
		}
		if (!SourceFetchSlots.sleep(PollSchedule.remaining(slot.start(), this.clock.instant()))) {
			return;
		}
		PollInterval interval = target.interval(context.operator());
		FetchResult result = this.articleSource.fetch(target.descriptor(), state.fetchState());
		PollOutcome outcome = state.complete(result, interval);
		FeedId feedId = state.feedId();
		Boolean saved = this.transaction.execute(status -> {
			if (!this.pollStates.replaceIfLeased(token, outcome.state())) {
				return false;
			}
			// 결과를 반영한 시각이 그 수집의 시각이다 — 새 항목은 성공한 수집에만 있다
			Instant fetchedAt = Objects.requireNonNull(outcome.state().lastPolledAt(), "lastPolledAt");
			for (FetchedArticle article : outcome.newArticles()) {
				this.events.publishEvent(new ArticleFetched(context.sourceId(), feedId, fetchedAt, article));
			}
			return true;
		});
		if (!Boolean.TRUE.equals(saved)) {
			logger.info("수집하는 사이 점유를 잃어 결과를 버린다 — 피드 {}", feedId.value());
			return;
		}
		log(context, target, result, outcome);
	}

	private static void log(FeedPollContext context, PollTarget.Ready target, FetchResult result,
			PollOutcome outcome) {
		long feedId = context.state().feedId().value();
		if (outcome.overflowSuspected()) {
			logger.warn("넘침 의심 — 피드 {}: 직전 수집과 겹치는 항목이 없다(직전 {}건 · 이번 {}건). 수집 주기를 줄일지 본다", feedId,
					sizeOf(context.state().previousItemKeys()), sizeOf(outcome.state().previousItemKeys()));
		}
		if (outcome.movedToChanged()) {
			logger.warn("피드가 영구 이동했다 — 피드 {}: {} → {}. 프로필의 주소를 고칠 단서다", feedId, target.feed().url(),
					outcome.state().movedTo());
		}
		switch (result) {
			case FetchResult.Fetched fetched -> {
			}
			case FetchResult.NotModified notModified -> {
			}
			case FetchResult.Failed failed -> logger.info("수집 실패 — 피드 {}: {} ({}, 연속 {}회, 다음 {})", feedId,
					failed.failure().message(), failed.failure().permanence(), outcome.state().consecutiveFailures(),
					outcome.state().nextPollAt());
		}
	}

	private static int sizeOf(@Nullable List<?> list) {
		return (list != null) ? list.size() : 0;
	}

}
