package net.yozmnews.storyline.application;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventView;
import net.yozmnews.storyline.LinkingFillResult;
import net.yozmnews.storyline.StorylineLinkingAdministration;
import net.yozmnews.storyline.domain.queue.LinkingQueueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공개 운영 API {@link StorylineLinkingAdministration} 의 구현 — docs/specs/storyline.md §4 · storyline-R15. 역할 애너테이션이
 * 없다(두 역할 공통).
 * <p>
 * 사건은 {@code event} 의 공개 조회 하나로 고르고 한 문장으로 넣는다 — 한 트랜잭션이다. 순서 키는 사건의 첫 기사 시각이다.
 */
@Service
class StorylineLinkingAdministrationService implements StorylineLinkingAdministration {

	private final EventDirectory events;

	private final LinkingQueueRepository queue;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	StorylineLinkingAdministrationService(EventDirectory events, LinkingQueueRepository queue,
			TransactionTemplate transaction) {
		this(events, queue, transaction, InstantSource.system());
	}

	StorylineLinkingAdministrationService(EventDirectory events, LinkingQueueRepository queue,
			TransactionTemplate transaction, InstantSource clock) {
		this.events = events;
		this.queue = queue;
		this.transaction = transaction;
		this.clock = clock;
	}

	@Override
	public LinkingFillResult fill(int days) {
		if (days < 1 || days > MAX_DAYS) {
			return new LinkingFillResult.DaysOutOfRange(MAX_DAYS);
		}
		Instant now = this.clock.instant();
		LinkingFillResult filled = this.transaction.execute(status -> {
			List<EventView> targets = this.events.lastArticleSince(now.minus(Duration.ofDays(days)));
			Map<Long, Instant> orderAt = new LinkedHashMap<>();
			targets.forEach(event -> orderAt.put(event.id().value(), event.firstArticleAt()));
			return new LinkingFillResult.Filled(targets.size(), this.queue.enqueueAll(orderAt, now));
		});
		return Objects.requireNonNull(filled, "filled");
	}

}
