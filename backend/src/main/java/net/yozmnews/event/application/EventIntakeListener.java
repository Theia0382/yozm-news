package net.yozmnews.event.application;

import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.yozmnews.article.ArticleId;
import net.yozmnews.article.ArticleStored;
import net.yozmnews.article.ArticlesRemoved;
import net.yozmnews.event.domain.assignment.AssignmentQueueRepository;
import net.yozmnews.event.domain.event.EventArticle;
import net.yozmnews.event.domain.event.EventArticleRepository;
import net.yozmnews.event.domain.event.EventRepository;
import net.yozmnews.event.domain.event.EventShrink;
import net.yozmnews.event.domain.event.LockedEvent;
import net.yozmnews.event.domain.feature.MatchFeatures;
import net.yozmnews.event.domain.feature.MatchFeaturesRepository;
import net.yozmnews.event.domain.intake.IntakeQueueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * {@link ArticleStored} · {@link ArticlesRemoved} 리스너 — docs/specs/event.md §4 · event-R5 · event-R8.
 * <p>
 * 비동기 · 새 트랜잭션이고 다시 처리해도 결과가 같다. 임베더 · Nori 를 쓰지 않는다.
 * <p>
 * 지울 때는 한 트랜잭션에서 사건 묶기 대기 · 배정 대기 행 → 배정 행(기사 ID 순) → 사건(사건 ID 순) 순으로 잡는다 — 진행 중인 회차의 쓰기
 * 트랜잭션이 대기 행을 잡고 있으면 그 커밋을 기다려, 커밋 직전의 표현 · 배정을 놓치지 않는다. 지운 기사의 표현을 읽어 사건마다 새 값을 정하고
 * ({@link EventShrink}), 배정 · 후보를 지운 뒤 사건을 고치거나 지우고({@link EventShrinkWriter}), 표현 행은 마지막에 지운다.
 */
@Component
class EventIntakeListener {

	private final IntakeQueueRepository queue;

	private final AssignmentQueueRepository assignmentQueue;

	private final MatchFeaturesRepository features;

	private final EventRepository events;

	private final EventArticleRepository assignments;

	private final EventShrinkWriter shrinks;

	private final InstantSource clock;

	@Autowired
	EventIntakeListener(IntakeQueueRepository queue, AssignmentQueueRepository assignmentQueue,
			MatchFeaturesRepository features, EventRepository events, EventArticleRepository assignments,
			EventShrinkWriter shrinks) {
		this(queue, assignmentQueue, features, events, assignments, shrinks, InstantSource.system());
	}

	EventIntakeListener(IntakeQueueRepository queue, AssignmentQueueRepository assignmentQueue,
			MatchFeaturesRepository features, EventRepository events, EventArticleRepository assignments,
			EventShrinkWriter shrinks, InstantSource clock) {
		this.queue = queue;
		this.assignmentQueue = assignmentQueue;
		this.features = features;
		this.events = events;
		this.assignments = assignments;
		this.shrinks = shrinks;
		this.clock = clock;
	}

	@ApplicationModuleListener
	public void on(ArticleStored event) {
		this.queue.enqueue(event.articleId(), this.clock.instant());
	}

	@ApplicationModuleListener
	public void on(ArticlesRemoved event) {
		List<ArticleId> removed = event.articleIds();
		this.queue.delete(removed);
		this.assignmentQueue.delete(removed);
		List<EventArticle> rows = this.assignments.lockByArticles(removed);
		Map<Long, List<ArticleId>> byEvent = new TreeMap<>();
		for (EventArticle row : rows) {
			byEvent.computeIfAbsent(row.eventId(), id -> new ArrayList<>()).add(row.articleId());
		}
		List<Planned> plans = new ArrayList<>();
		for (LockedEvent locked : this.events.lockAll(byEvent.keySet())) {
			List<ArticleId> members = byEvent.get(locked.id());
			Map<ArticleId, MatchFeatures> representations = this.features.findAll(members, locked.modelVersion())
				.stream()
				.collect(Collectors.toMap(MatchFeatures::articleId, Function.identity()));
			plans.add(new Planned(locked, EventShrink.of(locked, members, representations)));
		}
		this.assignments.deleteByArticles(removed);
		for (Planned plan : plans) {
			this.shrinks.write(plan.locked(), plan.shrink());
		}
		this.features.deleteByArticles(removed);
	}

	/** 잠근 사건과 그 새 값 */
	private record Planned(LockedEvent locked, EventShrink shrink) {
	}

}
