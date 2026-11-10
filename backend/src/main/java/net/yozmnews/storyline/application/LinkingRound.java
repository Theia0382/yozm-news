package net.yozmnews.storyline.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

import net.yozmnews.storyline.domain.linking.LinkingCriteria;
import net.yozmnews.storyline.domain.linking.LinkingCriteriaRepository;
import net.yozmnews.storyline.domain.queue.LinkingQueueRepository;
import net.yozmnews.storyline.domain.queue.LinkingSchedule;
import net.yozmnews.storyline.domain.queue.PendingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 잇기 회차 하나 — docs/specs/storyline.md §4 · storyline-R3 · storyline-R4.
 * <p>
 * 지금 기준을 기준 이력에 등록하고, 대기를 순서 키 순으로 점유해 커밋한 뒤, 사건마다 한 트랜잭션에서 {@link StorylineLinker} 로 판정하고
 * 점유 토큰과 다시 알림 수가 그대로일 때만 대기 행을 지운다 — 달라졌으면 점유만 푼다. 판정이 예외를 내면 그 트랜잭션은 롤백되고, 새
 * 트랜잭션에서 실패 수를 올리고 다음 사건으로 넘어간다. 실패 수가 상한이면 대기에서 뺀다. 판정의 예외를 밖으로 던지지 않는다. 트랜잭션 ·
 * 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
public class LinkingRound {

	private static final Logger logger = LoggerFactory.getLogger(LinkingRound.class);

	private final LinkingQueueRepository queue;

	private final LinkingCriteriaRepository criteriaHistory;

	private final LinkingCriteria criteria;

	private final StorylineLinker linker;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	LinkingRound(LinkingQueueRepository queue, LinkingCriteriaRepository criteriaHistory, LinkingCriteria criteria,
			StorylineLinker linker, TransactionTemplate transaction) {
		this(queue, criteriaHistory, criteria, linker, transaction, InstantSource.system());
	}

	/** 시험이 시계를 넣는다 */
	LinkingRound(LinkingQueueRepository queue, LinkingCriteriaRepository criteriaHistory, LinkingCriteria criteria,
			StorylineLinker linker, TransactionTemplate transaction, InstantSource clock) {
		this.queue = queue;
		this.criteriaHistory = criteriaHistory;
		this.criteria = criteria;
		this.linker = linker;
		this.transaction = transaction;
		this.clock = clock;
	}

	/** 회차 하나를 돈다 */
	public void run() {
		Instant now = this.clock.instant();
		Long criteriaId = this.transaction.execute(status -> this.criteriaHistory.register(this.criteria, now));
		long registered = Objects.requireNonNull(criteriaId, "criteriaId");
		for (PendingEvent claim : claim(now)) {
			UUID token = Objects.requireNonNull(claim.lease(), "lease").token();
			try {
				this.transaction.executeWithoutResult(status -> {
					this.linker.link(claim.eventId(), this.criteria, registered, this.clock.instant());
					if (!this.queue.deleteIfUnchanged(claim.eventId(), token, claim.notifications())) {
						// 처리 중에 다시 알림이 왔다 — 다음 회차가 지금 상태로 다시 판정한다(storyline-R3)
						this.queue.releaseIfLeased(claim.eventId(), token);
					}
				});
			}
			catch (RuntimeException ex) {
				fail(claim.eventId(), token, ex);
			}
		}
	}

	private List<PendingEvent> claim(Instant now) {
		List<PendingEvent> claimed = this.transaction.execute(status -> {
			List<PendingEvent> leased = new ArrayList<>();
			for (PendingEvent due : this.queue.lockDue(now, LinkingSchedule.BATCH_SIZE)) {
				PendingEvent next = due.claim(UUID.randomUUID(), now);
				this.queue.saveLocked(next);
				leased.add(next);
			}
			return leased;
		});
		return Objects.requireNonNull(claimed, "claimed");
	}

	/**
	 * 실패 수를 하나 올리고 점유를 푼다 — 상한이면 대기에서 뺀다(storyline-R4). 실패 기록 자체가 실패하면(DB 장애 등) 점유 쓰기처럼 회차
	 * 밖으로 나가고, 점유가 만료된 뒤 다시 처리된다.
	 */
	private void fail(long eventId, UUID token, RuntimeException cause) {
		this.transaction.executeWithoutResult(status -> {
			OptionalInt failures = this.queue.recordFailureIfLeased(eventId, token);
			if (failures.isEmpty()) {
				return;
			}
			int count = failures.getAsInt();
			logger.error("스토리라인 잇기 판정이 예상하지 못한 오류로 실패했다 — 사건 {}, 실패 {}회", eventId, count, cause);
			if (LinkingSchedule.failuresExhausted(count)) {
				this.queue.delete(eventId);
				logger.error("스토리라인 잇기 판정이 거듭 실패해 대기에서 뺐다 — 사건 {}, 실패 {}회", eventId, count);
			}
		});
	}

}
