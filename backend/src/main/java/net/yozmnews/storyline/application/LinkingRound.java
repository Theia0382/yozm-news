package net.yozmnews.storyline.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

import net.yozmnews.evaluation.FlowCandidate;
import net.yozmnews.evaluation.FlowMembership;
import net.yozmnews.evaluation.FlowMembershipJudgement;
import net.yozmnews.evaluation.FlowMembershipQuery;
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
 * 잇기 회차 하나 — docs/specs/storyline.md §4 · storyline-R3 · storyline-R4 · storyline-R36.
 * <p>
 * 지금 기준을 기준 이력에 등록하고, 대기를 순서 키 순으로 점유해 커밋한 뒤 사건마다 판정을 시도한다. 시도는 한 트랜잭션에서
 * {@link StorylineLinker#link} 를 부르고, 마쳤으면 점유 토큰과 다시 알림 수가 그대로일 때만 대기 행을 지운다 — 달라졌으면 점유만 푼다.
 * 물을 것이 있으면 트랜잭션 밖에서 {@link FlowMembership} 에 묻고 짧은 트랜잭션에서 답을 기록한 뒤 다시 시도한다 — 사건 하나에
 * {@link LinkingSchedule#MAX_ATTEMPTS_PER_CLAIM} 번까지, 남으면 점유를 풀어 다음 회차가 잇는다. 평가기 일시 실패면 실패를 세고 판정 가능
 * 시각을 미룬다. 평가기를 쓸 수 없으면 이 사건부터 남은 점유를 풀고 회차를 멈춘다. 판정이 예상 못한 예외를 내면 그 트랜잭션은 롤백되고,
 * 새 트랜잭션에서 실패 수를 올리고 다음 사건으로 넘어간다. 실패 수가 상한이면 대기에서 뺀다. 판정의 예외를 밖으로 던지지 않는다. 트랜잭션 ·
 * 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
public class LinkingRound {

	private static final Logger logger = LoggerFactory.getLogger(LinkingRound.class);

	private final LinkingQueueRepository queue;

	private final LinkingCriteriaRepository criteriaHistory;

	private final LinkingCriteria criteria;

	private final StorylineLinker linker;

	private final FlowMembership membership;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	LinkingRound(LinkingQueueRepository queue, LinkingCriteriaRepository criteriaHistory, LinkingCriteria criteria,
			StorylineLinker linker, FlowMembership membership, TransactionTemplate transaction) {
		this(queue, criteriaHistory, criteria, linker, membership, transaction, InstantSource.system());
	}

	/** 시험이 시계를 넣는다 */
	LinkingRound(LinkingQueueRepository queue, LinkingCriteriaRepository criteriaHistory, LinkingCriteria criteria,
			StorylineLinker linker, FlowMembership membership, TransactionTemplate transaction, InstantSource clock) {
		this.queue = queue;
		this.criteriaHistory = criteriaHistory;
		this.criteria = criteria;
		this.linker = linker;
		this.membership = membership;
		this.transaction = transaction;
		this.clock = clock;
	}

	/** 회차 하나를 돈다 */
	public void run() {
		Instant now = this.clock.instant();
		Long criteriaId = this.transaction.execute(status -> this.criteriaHistory.register(this.criteria, now));
		long registered = Objects.requireNonNull(criteriaId, "criteriaId");
		List<PendingEvent> claimed = claim(now);
		for (int i = 0; i < claimed.size(); i++) {
			PendingEvent claim = claimed.get(i);
			UUID token = Objects.requireNonNull(claim.lease(), "lease").token();
			try {
				if (judge(claim, token, registered) == Next.STOP) {
					release(claimed.subList(i, claimed.size()));
					return;
				}
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

	/** 사건 하나 — 시도 · 묻기 · 기록을 되풀이한다 */
	private Next judge(PendingEvent claim, UUID token, long criteriaId) {
		for (int attempt = 0; attempt < LinkingSchedule.MAX_ATTEMPTS_PER_CLAIM; attempt++) {
			LinkingOutcome outcome = Objects.requireNonNull(this.transaction.execute(status -> {
				LinkingOutcome tried = this.linker.link(claim.eventId(), this.criteria, criteriaId, this.clock.instant());
				switch (tried) {
					case LinkingOutcome.Done done -> finish(claim, token);
					case LinkingOutcome.Ask ask -> {
						// 아무것도 쓰지 않았다 — 트랜잭션 밖에서 묻는다
					}
				}
				return tried;
			}), "outcome");
			Next next = switch (outcome) {
				case LinkingOutcome.Done done -> Next.EVENT;
				case LinkingOutcome.Ask ask -> ask(claim, token, ask);
			};
			if (next != Next.ATTEMPT) {
				return next;
			}
		}
		// 시도를 다 썼다 — 다음 회차가 지금 상태로 잇는다
		this.transaction.executeWithoutResult(status -> this.queue.releaseIfLeased(claim.eventId(), token));
		return Next.EVENT;
	}

	/** 마친 사건의 대기 행 — 처리 중에 다시 알림이 왔으면 점유만 푼다(storyline-R3) */
	private void finish(PendingEvent claim, UUID token) {
		if (!this.queue.deleteIfUnchanged(claim.eventId(), token, claim.notifications())) {
			this.queue.releaseIfLeased(claim.eventId(), token);
		}
	}

	/** 띠 후보를 묻고 결과를 기록한다 — 묻기는 트랜잭션 밖이다 */
	private Next ask(PendingEvent claim, UUID token, LinkingOutcome.Ask ask) {
		List<FlowCandidate> candidates = ask.flows()
			.stream()
			.filter(flow -> !flow.articleIds().isEmpty())
			.map(flow -> new FlowCandidate(flow.flowId(), flow.articleIds()))
			.toList();
		FlowMembershipJudgement judgement = candidates.isEmpty() ? new FlowMembershipJudgement.Judged(null, Map.of())
				: this.membership.judge(new FlowMembershipQuery(ask.article(), candidates));
		return switch (judgement) {
			case FlowMembershipJudgement.Judged judged -> {
				this.transaction.executeWithoutResult(status -> this.linker.record(ask, judged.judgement(),
						judged.probabilities(), this.clock.instant()));
				yield Next.ATTEMPT;
			}
			case FlowMembershipJudgement.ArticleMissing missing -> {
				// 대표 기사가 지워졌다 — 사건이 바뀌어 알림이 다시 넣는다
				this.transaction.executeWithoutResult(status -> this.queue.releaseIfLeased(claim.eventId(), token));
				yield Next.EVENT;
			}
			case FlowMembershipJudgement.Failed failed -> switch (failed.kind()) {
				case TRANSIENT -> {
					defer(claim.eventId(), token);
					yield Next.EVENT;
				}
				case ARTICLE_REJECTED -> {
					// 같은 기사라 다시 보내도 같다 — 물은 후보를 확률 없이 기록하고 판정을 잇는다(D10)
					this.transaction.executeWithoutResult(
							status -> this.linker.record(ask, null, Map.of(), this.clock.instant()));
					yield Next.ATTEMPT;
				}
				case EVALUATOR_UNAVAILABLE -> {
					logger.error("스토리라인 잇기 판정을 멈췄다 — 평가기를 쓸 수 없다: {}", failed.message());
					yield Next.STOP;
				}
			};
		};
	}

	/** 평가기 일시 실패 — 실패 수를 올리고 판정 가능 시각을 미룬다. 상한이면 대기에서 뺀다(storyline-R4) */
	private void defer(long eventId, UUID token) {
		this.transaction.executeWithoutResult(status -> {
			OptionalInt failures = this.queue.recordFailureIfLeased(eventId, token);
			if (failures.isEmpty()) {
				return;
			}
			int count = failures.getAsInt();
			if (LinkingSchedule.failuresExhausted(count)) {
				this.queue.delete(eventId);
				logger.error("스토리라인 잇기 판정이 거듭 실패해 대기에서 뺐다 — 사건 {}, 실패 {}회", eventId, count);
				return;
			}
			this.queue.defer(eventId, LinkingSchedule.retryAt(count, this.clock.instant()));
		});
	}

	/** 남은 점유를 푼다 — 회차를 멈출 때 */
	private void release(List<PendingEvent> remaining) {
		this.transaction.executeWithoutResult(status -> remaining.forEach(claim -> this.queue
			.releaseIfLeased(claim.eventId(), Objects.requireNonNull(claim.lease(), "lease").token())));
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

	/** 다음에 할 것 */
	private enum Next {

		/** 같은 사건을 다시 시도한다 */
		ATTEMPT,

		/** 다음 사건 */
		EVENT,

		/** 회차를 멈춘다 */
		STOP

	}

}
