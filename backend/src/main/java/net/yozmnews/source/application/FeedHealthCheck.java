package net.yozmnews.source.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.yozmnews.source.FeedStatus;
import net.yozmnews.source.FeedStatusTransition;
import net.yozmnews.source.FeedStatusesChanged;
import net.yozmnews.source.domain.poll.FeedPollState;
import net.yozmnews.source.domain.poll.FeedStatusRepository;
import net.yozmnews.source.domain.poll.FeedStatusRule;
import net.yozmnews.source.domain.poll.FeedStatusTarget;
import net.yozmnews.source.domain.poll.LastError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 켜진 피드의 상태를 다시 판정하는 피드 건강 검사 — docs/specs/source.md §4 · source-R30 · source-R31 · source-R47 ~
 * source-R49.
 * <p>
 * 바뀐 피드가 있으면 상태를 쓴 트랜잭션에서 {@link FeedStatusesChanged} 를 한 번 발행한다.
 */
@Service
public class FeedHealthCheck {

	private static final Logger logger = LoggerFactory.getLogger(FeedHealthCheck.class);

	private static final String CHANGED = "피드 상태가 바뀌었다 — 피드 {}: {} → {} (연속 실패 {}회 · 그중 영구 {}회, 마지막 실패 {}, "
			+ "마지막 새 항목 {}, 새 항목의 가장 늦은 발행 시각 {})";

	private final FeedStatusRepository statuses;

	private final TransactionTemplate transaction;

	private final ApplicationEventPublisher events;

	private final InstantSource clock;

	@Autowired
	FeedHealthCheck(FeedStatusRepository statuses, TransactionTemplate transaction, ApplicationEventPublisher events) {
		this(statuses, transaction, events, InstantSource.system());
	}

	FeedHealthCheck(FeedStatusRepository statuses, TransactionTemplate transaction, ApplicationEventPublisher events,
			InstantSource clock) {
		this.statuses = statuses;
		this.transaction = transaction;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * 판정해 바뀐 피드의 상태를 쓰고 같은 트랜잭션에서 전이를 발행한 뒤, 커밋 뒤 전이를 로그한다.
	 * @return 이번에 바뀐 피드 — 피드 ID 순
	 */
	public List<FeedStatusChange> run() {
		Instant now = this.clock.instant();
		List<FeedStatusChange> changes = this.transaction.execute(status -> judge(now));
		for (FeedStatusChange change : changes) {
			log(change);
		}
		return changes;
	}

	private List<FeedStatusChange> judge(Instant now) {
		List<FeedStatusChange> changes = new ArrayList<>();
		for (FeedStatusTarget target : this.statuses.lockTargets()) {
			Optional<FeedStatus> next = FeedStatusRule.transition(target.status(), target.state(), now);
			if (next.isPresent()) {
				this.statuses.saveStatus(target.state().feedId(), next.get(), now);
				changes.add(new FeedStatusChange(target.state(), target.status(), next.get(), now));
			}
		}
		if (!changes.isEmpty()) {
			this.events.publishEvent(new FeedStatusesChanged(now, changes.stream()
				.map(change -> new FeedStatusTransition(change.state().feedId(), change.from(), change.to()))
				.toList()));
		}
		return changes;
	}

	private static void log(FeedStatusChange change) {
		FeedPollState state = change.state();
		LastError error = state.lastError();
		Object[] arguments = { state.feedId().value(), change.from(), change.to(), state.consecutiveFailures(),
				state.consecutivePermanentFailures(),
				(error != null) ? error.reason() + "(" + error.reason().permanence() + ")" : "없음",
				state.lastNewItemAt(), state.latestPublishedAt() };
		Level level = switch (change.to()) {
			case DEGRADED, STALE -> Level.WARN;
			case ACTIVE -> Level.INFO;
		};
		logger.atLevel(level).log(CHANGED, arguments);
	}

}
