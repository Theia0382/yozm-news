package net.yozmnews.source.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.poll.FeedPollContext;
import net.yozmnews.source.domain.poll.FeedPollStateRepository;
import net.yozmnews.source.domain.poll.Lease;
import net.yozmnews.source.domain.poll.PollSlots;
import net.yozmnews.source.domain.registry.ActivationRefusal;
import net.yozmnews.source.domain.registry.PollTarget;
import net.yozmnews.source.domain.registry.ProfileResolution;
import net.yozmnews.source.domain.registry.ProfileResolver;
import net.yozmnews.source.domain.registry.Source;
import net.yozmnews.source.domain.registry.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 지금 수집할 피드의 점유 — docs/specs/source.md §4 · source-R15 · source-R19
 */
@Service
public class PollScheduler {

	/** 한 회차에 점유하는 최대 피드 수 — 측정 전 목표다 */
	static final int CLAIM_BATCH = 100;

	private static final Logger logger = LoggerFactory.getLogger(PollScheduler.class);

	private final FeedPollStateRepository pollStates;

	private final SourceRepository sources;

	private final ProfileResolver resolver;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	PollScheduler(FeedPollStateRepository pollStates, SourceRepository sources, ProfileResolver resolver,
			TransactionTemplate transaction) {
		this(pollStates, sources, resolver, transaction, InstantSource.system());
	}

	PollScheduler(FeedPollStateRepository pollStates, SourceRepository sources, ProfileResolver resolver,
			TransactionTemplate transaction, InstantSource clock) {
		this.pollStates = pollStates;
		this.sources = sources;
		this.resolver = resolver;
		this.transaction = transaction;
		this.clock = clock;
	}

	/**
	 * 지금 수집할 피드를 점유한다.
	 * @return 점유한 수집 — 예약 시각 순이 아닐 수 있다
	 */
	public List<ClaimedPoll> claimDueFeeds() {
		Instant now = this.clock.instant();
		List<Deferred> deferred = new ArrayList<>();
		List<ClaimedPoll> claimed = this.transaction.execute(status -> claim(now, deferred));
		for (Deferred item : deferred) {
			logger.warn("프로필에서 피드를 얻지 못해 수집을 미룬다 — 소스 {} 피드 {}({}): {}", item.source().value(),
					item.context().state().feedId().value(), item.context().feedKey(), item.reason());
		}
		return claimed;
	}

	private List<ClaimedPoll> claim(Instant now, List<Deferred> deferred) {
		List<FeedPollContext> due = this.pollStates.lockDue(now, CLAIM_BATCH);
		if (due.isEmpty()) {
			return List.of();
		}
		Map<SourceId, ProfileResolution> resolutions = resolve(
				due.stream().map(FeedPollContext::sourceId).collect(Collectors.toSet()));
		Map<SourceId, List<FeedPollContext>> ready = new LinkedHashMap<>();
		for (FeedPollContext context : due) {
			switch (PollTarget.of(resolutions.get(context.sourceId()), context.feedKey())) {
				case PollTarget.Ready target -> ready.computeIfAbsent(context.sourceId(), id -> new ArrayList<>())
					.add(context);
				case PollTarget.Unavailable unavailable -> {
					this.pollStates.saveLocked(context.state().deferForUnavailableProfile(now));
					deferred.add(new Deferred(context.sourceId(), context, unavailable.reason()));
				}
			}
		}
		Map<SourceId, Instant> lastScheduled = this.pollStates.lastScheduledBySource(ready.keySet(), now);
		List<ClaimedPoll> claimed = new ArrayList<>();
		ready.forEach((source, contexts) -> {
			List<Instant> slots = PollSlots.assign(now, lastScheduled.get(source), contexts.size());
			for (int i = 0; i < contexts.size(); i++) {
				Lease lease = new Lease(UUID.randomUUID(), slots.get(i));
				FeedPollContext context = contexts.get(i);
				this.pollStates.saveLocked(context.state().claim(lease, now));
				claimed.add(new ClaimedPoll(context.state().feedId(), lease.token(), lease.scheduledAt()));
			}
		});
		return claimed;
	}

	private Map<SourceId, ProfileResolution> resolve(Set<SourceId> ids) {
		Map<SourceId, ProfileResolution> resolutions = new LinkedHashMap<>();
		for (Source source : this.sources.findAllById(ids.stream().map(SourceId::value).toList())) {
			resolutions.put(source.id(), this.resolver.resolve(source));
		}
		return resolutions;
	}

	private record Deferred(SourceId source, FeedPollContext context, ActivationRefusal reason) {
	}

}
