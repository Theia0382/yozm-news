package net.yozmnews.storyline.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;

import net.yozmnews.platform.RoundActivity;
import net.yozmnews.platform.RoundQueue;
import net.yozmnews.storyline.StorylineProcessing;
import net.yozmnews.storyline.domain.backlog.Backlog;
import net.yozmnews.storyline.domain.queue.LinkingQueueRepository;
import net.yozmnews.storyline.domain.relation.RelationQueueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link StorylineProcessing} 의 구현 — docs/specs/storyline.md §3.
 * <p>
 * 남은 수는 대기마다 한 문장으로 센다. 처리량은 {@link RoundActivity} 가 낸다.
 */
@Service
class StorylineProcessingService implements StorylineProcessing {

	private final LinkingQueueRepository linking;

	private final RelationQueueRepository relation;

	private final RoundActivity activity;

	private final InstantSource clock;

	StorylineProcessingService(LinkingQueueRepository linking, RelationQueueRepository relation,
			RoundActivity activity, InstantSource clock) {
		this.linking = linking;
		this.relation = relation;
		this.activity = activity;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public List<RoundQueue> rounds() {
		Instant now = this.clock.instant();
		return List.of(queue(LinkingRound.NAME, this.linking.backlog(now)),
				queue(RelationRound.NAME, this.relation.backlog(now)));
	}

	private RoundQueue queue(String round, Backlog backlog) {
		return new RoundQueue(round, backlog.waiting(), backlog.deferred(), this.activity.recent(round));
	}

}
