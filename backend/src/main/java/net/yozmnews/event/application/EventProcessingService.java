package net.yozmnews.event.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Objects;

import net.yozmnews.event.AssignmentCriteria;
import net.yozmnews.event.EventProcessing;
import net.yozmnews.event.domain.assignment.AssignmentQueueRepository;
import net.yozmnews.event.domain.assignment.CriteriaRepository;
import net.yozmnews.event.domain.backlog.Backlog;
import net.yozmnews.event.domain.event.EventArticleRepository;
import net.yozmnews.event.domain.event.EventRepository;
import net.yozmnews.event.domain.intake.IntakeQueueRepository;
import net.yozmnews.platform.RoundActivity;
import net.yozmnews.platform.RoundQueue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link EventProcessing} 의 구현 — docs/specs/event.md §3.
 * <p>
 * 남은 수는 대기마다 한 문장으로 센다. 재판정 · 병합은 지금 기준의 ID 를 기준 이력에서 찾기만 하고 등록하지 않는다 — 아직 없으면 모든 배정 ·
 * 열린 사건이 대상이다. 처리량은 {@link RoundActivity} 가 낸다.
 */
@Service
class EventProcessingService implements EventProcessing {

	private final IntakeQueueRepository intake;

	private final AssignmentQueueRepository assignmentQueue;

	private final EventArticleRepository assignments;

	private final EventRepository events;

	private final CriteriaRepository criteriaHistory;

	private final AssignmentCriteria criteria;

	private final RoundActivity activity;

	private final InstantSource clock;

	EventProcessingService(IntakeQueueRepository intake, AssignmentQueueRepository assignmentQueue,
			EventArticleRepository assignments, EventRepository events, CriteriaRepository criteriaHistory,
			AssignmentSettings settings, RoundActivity activity, InstantSource clock) {
		this.intake = intake;
		this.assignmentQueue = assignmentQueue;
		this.assignments = assignments;
		this.events = events;
		this.criteriaHistory = criteriaHistory;
		this.criteria = Objects.requireNonNull(settings, "settings").criteria();
		this.activity = activity;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public List<RoundQueue> rounds() {
		Instant now = this.clock.instant();
		long criteriaId = this.criteriaHistory.find(this.criteria).orElse(EventArticleRepository.NO_CRITERIA);
		Backlog rejudge = new Backlog(this.assignments.countRejudgeTargets(criteriaId), 0);
		return List.of(queue(EventIntakeRound.NAME, this.intake.backlog()),
				queue(AssignmentRound.NAME, this.assignmentQueue.backlog(now)), queue(RejudgementRound.NAME, rejudge),
				queue(MergeRound.NAME, this.events.mergeBacklog(criteriaId, now)));
	}

	private RoundQueue queue(String round, Backlog backlog) {
		return new RoundQueue(round, backlog.waiting(), backlog.deferred(), this.activity.recent(round));
	}

}
