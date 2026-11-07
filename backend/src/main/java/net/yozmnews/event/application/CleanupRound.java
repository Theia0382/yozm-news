package net.yozmnews.event.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.domain.assignment.AssignmentCriteria;
import net.yozmnews.event.domain.assignment.AssignmentSchedule;
import net.yozmnews.event.domain.event.EventClosing;
import net.yozmnews.event.domain.event.EventQueryRepository;
import net.yozmnews.event.domain.event.EventRepository;
import net.yozmnews.event.domain.event.LockedEvent;
import net.yozmnews.event.domain.event.LongTermIndexes;
import net.yozmnews.event.domain.feature.FeatureRetention;
import net.yozmnews.event.domain.feature.MatchFeaturesRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 하루 한 번 사건 정리 — 닫기 · 장기 검색 인덱스 · 기사 표현 정리를 차례로 한다 · docs/specs/event.md §4 · event-R13.
 * <p>
 * 닫기는 시계를 한 번 읽어, 닫을 수 있는 사건을 커서로 읽고 사건마다 한 트랜잭션에서 잠가 {@link EventClosing#due} 를 다시 본 뒤 대표 기사(사건
 * 기사 전체에서 중심에 가까운 순 — {@link EventQueryRepository#representativesOfAll})와 함께 닫는다. 인덱스는 트랜잭션 밖에서 만든다.
 * 표현 정리는 닫힌 사건 기사의 표현과, 만든 뒤 시간 창 + 여유가 지났고 그 모델 버전의 열린 사건에 배정되지 않은 표현을 묶음마다 지운다 —
 * 표현은 대표 기사를 고른 뒤에 지운다. 단계마다 예상 못한 예외는 그 단계에서 멈추고 다음 단계로 간다. 다시 돌려도 결과가 같다. 예외를 밖으로 던지지 않는다. 트랜잭션 · 역할
 * 애너테이션이 없다(두 역할 공통).
 */
@Service
public class CleanupRound {

	private static final Logger logger = LoggerFactory.getLogger(CleanupRound.class);

	private final EventRepository events;

	private final EventQueryRepository queries;

	private final MatchFeaturesRepository features;

	private final LongTermIndexes indexes;

	private final EventClosing closing;

	private final AssignmentCriteria criteria;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	CleanupRound(EventRepository events, EventQueryRepository queries, MatchFeaturesRepository features,
			LongTermIndexes indexes, EventClosing closing, AssignmentCriteria criteria, TransactionTemplate transaction) {
		this(events, queries, features, indexes, closing, criteria, transaction, InstantSource.system());
	}

	/** 시험이 시계를 넣는다 */
	CleanupRound(EventRepository events, EventQueryRepository queries, MatchFeaturesRepository features,
			LongTermIndexes indexes, EventClosing closing, AssignmentCriteria criteria, TransactionTemplate transaction,
			InstantSource clock) {
		this.events = events;
		this.queries = queries;
		this.features = features;
		this.indexes = indexes;
		this.closing = closing;
		this.criteria = criteria;
		this.transaction = transaction;
		this.clock = clock;
	}

	/** 정리 하나를 돈다 */
	public void run() {
		Instant now = this.clock.instant();
		closeDue(now);
		ensureIndexes();
		deleteFeatures(now);
	}

	private void closeDue(Instant now) {
		int closed = 0;
		try {
			long after = 0;
			while (true) {
				List<Long> due = this.events.closable(this.closing.idleBefore(now), this.closing.openedBefore(now), after,
						AssignmentSchedule.CLEANUP_BATCH);
				if (due.isEmpty()) {
					break;
				}
				for (long eventId : due) {
					if (Boolean.TRUE.equals(this.transaction.execute(status -> close(eventId, now)))) {
						closed++;
					}
					after = eventId;
				}
			}
		}
		catch (RuntimeException ex) {
			logger.error("사건 닫기가 예상하지 못한 오류로 멈췄다 — 닫은 사건 {}건", closed, ex);
		}
		if (closed > 0) {
			logger.info("사건 {}건을 닫았다", closed);
		}
	}

	/** 잠가 다시 보고 닫는다 — 트랜잭션 안. 닫았으면 true */
	private boolean close(long eventId, Instant now) {
		Optional<LockedEvent> locked = this.events.lock(eventId);
		if (locked.isEmpty() || !this.closing.due(locked.get(), now)) {
			return false;
		}
		LockedEvent event = locked.get();
		// 대표는 사건 기사 전체에서 중심에 가까운 순(공개 조회와 같은 정렬) — 닫은 뒤에는 표현을 지워 다시 고를 수 없다
		@Nullable ArticleId representative = this.queries.representativesOfAll(List.of(event.id())).get(event.id());
		this.events.close(event.id(), representative, now);
		return true;
	}

	private void ensureIndexes() {
		try {
			int created = this.indexes.ensure();
			if (created > 0) {
				logger.info("장기 검색 인덱스 {}개를 만들었다", created);
			}
		}
		catch (RuntimeException ex) {
			logger.error("장기 검색 인덱스 확인이 예상하지 못한 오류로 멈췄다", ex);
		}
	}

	private void deleteFeatures(Instant now) {
		int deleted = 0;
		try {
			deleted += drain(() -> this.features.deleteOfClosedEvents(AssignmentSchedule.CLEANUP_BATCH));
			Instant before = FeatureRetention.unassignedBefore(now, this.criteria.window());
			deleted += drain(() -> this.features.deleteUnassigned(before, AssignmentSchedule.CLEANUP_BATCH));
		}
		catch (RuntimeException ex) {
			logger.error("기사 표현 정리가 예상하지 못한 오류로 멈췄다 — 지운 표현 {}건", deleted, ex);
		}
		if (deleted > 0) {
			logger.info("기사 표현 {}건을 지웠다", deleted);
		}
	}

	/** 한 묶음씩 트랜잭션에서 지워 0 이 될 때까지 — 지운 수 */
	private int drain(Batch batch) {
		int total = 0;
		while (true) {
			int deleted = Objects.requireNonNull(this.transaction.execute(status -> batch.delete()), "deleted");
			if (deleted == 0) {
				return total;
			}
			total += deleted;
		}
	}

	@FunctionalInterface
	private interface Batch {

		int delete();

	}

}
