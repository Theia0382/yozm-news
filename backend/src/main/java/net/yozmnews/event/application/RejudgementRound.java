package net.yozmnews.event.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.yozmnews.event.domain.assignment.AssignmentCriteria;
import net.yozmnews.event.domain.assignment.AssignmentRule;
import net.yozmnews.event.domain.assignment.AssignmentSchedule;
import net.yozmnews.event.domain.assignment.CriteriaRepository;
import net.yozmnews.event.domain.assignment.Decision;
import net.yozmnews.event.domain.assignment.DecisionPhase;
import net.yozmnews.event.domain.assignment.Reassignment;
import net.yozmnews.event.domain.event.EventArticle;
import net.yozmnews.event.domain.event.EventArticleRepository;
import net.yozmnews.event.domain.event.EventRepository;
import net.yozmnews.event.domain.event.EventShrink;
import net.yozmnews.event.domain.event.EventStatus;
import net.yozmnews.event.domain.event.LockedEvent;
import net.yozmnews.event.domain.event.RejudgeTarget;
import net.yozmnews.event.domain.feature.MatchFeatures;
import net.yozmnews.event.domain.feature.MatchFeaturesRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 재판정 회차 하나 — 지금 기준과 다른 기준으로 판정된 배정을 저장된 후보 점수 · 확률로 다시 판정한다 · docs/specs/event.md §4 ·
 * event-R18.
 * <p>
 * 사건 소속 판정 · 임베더를 부르지 않는다. 배정 하나마다 짧은 트랜잭션에서 배정 행 → 사건(ID 순)을 잠그고 결과를 적용한다 — 판정은
 * {@link AssignmentRule}, 적용 방식은 {@link Reassignment} 가 정한다. 옮기거나 떼어 낼 때의 기사 벡터는 지금 임베더가 아니라 그 배정이
 * 속한 사건의 모델 버전 표현이고, 없으면(보존 기간이 지나 지움) 옮기지 못해 기준만 새것으로 적는다. 회차마다
 * {@link AssignmentSchedule#REJUDGE_BATCH} 건까지 한다. 예외를 밖으로 던지지 않는다. 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
public class RejudgementRound {

	private static final Logger logger = LoggerFactory.getLogger(RejudgementRound.class);

	private final EventArticleRepository assignments;

	private final EventRepository events;

	private final MatchFeaturesRepository features;

	private final CriteriaRepository criteriaHistory;

	private final AssignmentCriteria criteria;

	private final EventShrinkWriter shrinks;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	RejudgementRound(EventArticleRepository assignments, EventRepository events, MatchFeaturesRepository features,
			CriteriaRepository criteriaHistory, AssignmentCriteria criteria, EventShrinkWriter shrinks,
			TransactionTemplate transaction) {
		this(assignments, events, features, criteriaHistory, criteria, shrinks, transaction, InstantSource.system());
	}

	/** 시험이 시계를 넣는다 */
	RejudgementRound(EventArticleRepository assignments, EventRepository events, MatchFeaturesRepository features,
			CriteriaRepository criteriaHistory, AssignmentCriteria criteria, EventShrinkWriter shrinks,
			TransactionTemplate transaction, InstantSource clock) {
		this.assignments = assignments;
		this.events = events;
		this.features = features;
		this.criteriaHistory = criteriaHistory;
		this.criteria = criteria;
		this.shrinks = shrinks;
		this.transaction = transaction;
		this.clock = clock;
	}

	/** 회차 하나를 돈다 */
	public void run() {
		long criteriaId = Objects.requireNonNull(
				this.transaction.execute(status -> this.criteriaHistory.register(this.criteria, this.clock.instant())),
				"criteriaId");
		int rejudged = 0;
		try {
			while (rejudged < AssignmentSchedule.REJUDGE_BATCH) {
				Instant now = this.clock.instant();
				Boolean found = this.transaction.execute(status -> {
					List<RejudgeTarget> targets = this.assignments.lockRejudgeTargets(criteriaId, 1);
					if (targets.isEmpty()) {
						return false;
					}
					apply(targets.get(0), criteriaId, now);
					return true;
				});
				if (!Boolean.TRUE.equals(found)) {
					break;
				}
				rejudged++;
			}
		}
		catch (RuntimeException ex) {
			logger.error("재판정 회차가 예상하지 못한 오류로 멈췄다 — 이번 회차에 다시 판정한 배정 {}건", rejudged, ex);
			return;
		}
		if (rejudged > 0) {
			logger.info("배정 {}건을 지금 기준으로 다시 판정했다", rejudged);
		}
	}

	/** 잠근 배정 하나를 다시 판정해 적용한다 — 트랜잭션 안 */
	private void apply(RejudgeTarget target, long criteriaId, Instant now) {
		EventArticle assignment = target.assignment();
		long current = assignment.eventId();
		Decision decision = AssignmentRule.decide(target.stored(), this.criteria, DecisionPhase.REJUDGE);
		// 사건을 고칠 수 있으면 관련 사건을 ID 순으로 잠근다 — 떼어 낼지는 잠근 뒤의 기사 수로 정한다
		Set<Long> touched = switch (decision) {
			case Decision.Attach attach -> (attach.eventId() == current) ? Set.of() : Set.of(current, attach.eventId());
			case Decision.Open open -> Set.of(current);
			case Decision.Keep keep -> Set.of();
			case Decision.Ask ask -> throw new IllegalStateException("재판정은 묻지 않는다: " + ask);
		};
		Map<Long, LockedEvent> locked = this.events.lockAll(touched)
			.stream()
			.collect(Collectors.toMap(LockedEvent::id, Function.identity()));
		@Nullable LockedEvent currentEvent = locked.get(current);
		int currentArticles = (currentEvent != null) ? currentEvent.articleCount() : 0;
		Reassignment reassignment = Reassignment.of(decision, current, target.stored(), currentArticles);
		switch (reassignment) {
			case Reassignment.Keep keep -> this.assignments.keep(assignment.articleId(), criteriaId);
			case Reassignment.Restamp restamp -> this.assignments.reassign(assignment.articleId(), current,
					restamp.score(), restamp.by(), criteriaId, now);
			case Reassignment.Move move -> {
				LockedEvent destination = locked.get(move.eventId());
				Optional<MatchFeatures> representation = representation(assignment, currentEvent);
				if (currentEvent == null || destination == null || destination.status() != EventStatus.OPEN
						|| !destination.modelVersion().equals(currentEvent.modelVersion()) || representation.isEmpty()) {
					this.assignments.keep(assignment.articleId(), criteriaId);
					return;
				}
				MatchFeatures features = representation.get();
				this.assignments.reassign(assignment.articleId(), destination.id(), move.score(), move.by(), criteriaId,
						now);
				this.events.update(destination.joined(features.embedding().values(), assignment.publishedAt()),
						features.entities(), List.of());
				leave(currentEvent, assignment, features);
			}
			case Reassignment.Split split -> {
				Optional<MatchFeatures> representation = representation(assignment, currentEvent);
				if (currentEvent == null || representation.isEmpty()) {
					this.assignments.keep(assignment.articleId(), criteriaId);
					return;
				}
				MatchFeatures features = representation.get();
				long opened = this.events.open(currentEvent.modelVersion(), features.embedding().values(),
						assignment.publishedAt(), features.entities(), now);
				this.assignments.reassign(assignment.articleId(), opened, split.bestScore(), split.by(), criteriaId, now);
				leave(currentEvent, assignment, features);
			}
		}
	}

	/** 그 배정이 속한 사건의 모델 버전 표현 */
	private Optional<MatchFeatures> representation(EventArticle assignment, @Nullable LockedEvent currentEvent) {
		if (currentEvent == null) {
			return Optional.empty();
		}
		return this.features.find(assignment.articleId(), currentEvent.modelVersion());
	}

	/** 옮긴 기사를 옛 사건에서 뺀다 — 배정을 옮긴 뒤라 비는 사건을 지워도 외래 키에 걸리지 않는다 */
	private void leave(LockedEvent currentEvent, EventArticle assignment, MatchFeatures features) {
		this.shrinks.write(currentEvent,
				EventShrink.of(currentEvent, List.of(assignment.articleId()), Map.of(assignment.articleId(), features)));
	}

}
