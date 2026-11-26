package net.yozmnews.tag.application;

import java.time.InstantSource;
import java.util.List;

import net.yozmnews.platform.RoundActivity;
import net.yozmnews.platform.RoundQueue;
import net.yozmnews.tag.TagProcessing;
import net.yozmnews.tag.domain.queue.EvaluationBacklog;
import net.yozmnews.tag.domain.queue.EvaluationQueueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link TagProcessing} 의 구현 — docs/specs/tag.md §3.
 * <p>
 * 남은 수 · 미룬 수는 평가 대기 건수 질의 하나에서 읽는다 — 운영 API 의 대기 건수({@code EvaluationQueueCounts})와 같은 질의다. 기사 거부로
 * 실패로 남은 대기는 회차가 다시 가져가지 않아 남은 수에 들지 않는다. 처리량은 {@link RoundActivity} 가 낸다.
 */
@Service
class TagProcessingService implements TagProcessing {

	private final EvaluationQueueRepository queue;

	private final RoundActivity activity;

	private final InstantSource clock;

	TagProcessingService(EvaluationQueueRepository queue, RoundActivity activity, InstantSource clock) {
		this.queue = queue;
		this.activity = activity;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public List<RoundQueue> rounds() {
		EvaluationBacklog counts = this.queue.counts(this.clock.instant());
		return List.of(new RoundQueue(EvaluationRound.NAME, counts.waiting(), counts.deferred(),
				this.activity.recent(EvaluationRound.NAME)));
	}

}
