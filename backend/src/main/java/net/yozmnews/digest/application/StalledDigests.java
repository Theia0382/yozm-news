package net.yozmnews.digest.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.Objects;
import java.util.UUID;

import net.yozmnews.digest.DigestFailure;
import net.yozmnews.digest.DigestStatus;
import net.yozmnews.digest.domain.digest.Digest;
import net.yozmnews.digest.domain.digest.DigestId;
import net.yozmnews.digest.domain.digest.DigestLedger;
import net.yozmnews.digest.domain.digest.DigestRepository;
import net.yozmnews.digest.domain.digest.ScheduledDigest;
import net.yozmnews.digest.domain.schedule.DigestScheduleRules;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 멈춘 다이제스트 거두기 회차 — 등록한 생성 작업이 시작되지 않은 다이제스트를 다시 등록하고, 끝나지 않는 다이제스트를 실패로 끝낸다.
 * docs/specs/digest.md §4
 * <p>
 * 생성을 기다린 채 등록한 지 {@link DigestScheduleRules#DISPATCH_WAIT} 가 지난 다이제스트는 새 토큰으로 다시 등록한다 — 생성기를 부르기 전이라
 * 비용이 없다. 만든 지 {@link DigestScheduleRules#START_DEADLINE} 이 지났으면 다시 등록하지 않고 {@link DigestFailure#INTERRUPTED} 로
 * 끝낸다. 생성 중인 채 시작한 지 {@link DigestScheduleRules#RUN_LEASE} 가 지난 다이제스트는 {@link DigestFailure#INTERRUPTED} 로 끝내고 다시
 * 돌리지 않는다 — 생성기를 부른 뒤 멈춘 생성을 다시 돌리면 비용을 되풀이할 수 있다. 행마다 트랜잭션 하나에서 행을 잠그고 조건을 다시 본 뒤
 * 바꾼다. 회차 하나는 갈래마다 한 쪽까지만 본다 — 남은 것은 다음 회차가 본다. 역할 애너테이션이 없다(부르는 반복 작업이 작업 역할이다).
 */
@Service
public class StalledDigests {

	/** 회차 하나가 갈래마다 보는 다이제스트 수 — 측정 전 목표 */
	static final int PAGE = 100;

	/** 시작 기한이 지났을 때의 실패 메시지 */
	static final String NOT_STARTED = "시작 기한 안에 생성이 시작되지 않았다";

	/** 임대가 지났을 때의 실패 메시지 */
	static final String NOT_FINISHED = "임대 안에 생성이 끝나지 않았다";

	private static final Logger logger = LoggerFactory.getLogger(StalledDigests.class);

	private final DigestRepository digests;

	private final DigestLedger ledger;

	private final DigestEndings endings;

	private final DigestDispatch dispatch;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	StalledDigests(DigestRepository digests, DigestLedger ledger, DigestEndings endings, DigestDispatch dispatch,
			TransactionTemplate transaction, InstantSource clock) {
		this.digests = Objects.requireNonNull(digests, "digests");
		this.ledger = Objects.requireNonNull(ledger, "ledger");
		this.endings = Objects.requireNonNull(endings, "endings");
		this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
		this.transaction = Objects.requireNonNull(transaction, "transaction");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/** 회차 하나 — 지금 시각 하나로 멈춘 등록 · 멈춘 생성을 한 쪽씩 본다 */
	public void runRound() {
		Instant now = this.clock.instant();
		for (DigestId id : this.ledger.stalledScheduled(now.minus(DigestScheduleRules.DISPATCH_WAIT), PAGE)) {
			try {
				@Nullable ScheduledDigest again = this.transaction.execute(status -> recoverScheduled(id, now));
				if (again != null) {
					this.dispatch.dispatch(again);
				}
			}
			catch (RuntimeException ex) {
				logger.warn("시작되지 않은 다이제스트를 거두지 못했다 — 다이제스트 {}", id.value(), ex);
			}
		}
		for (DigestId id : this.ledger.stalledRunning(now.minus(DigestScheduleRules.RUN_LEASE), PAGE)) {
			try {
				this.transaction.executeWithoutResult(status -> interruptRunning(id, now));
			}
			catch (RuntimeException ex) {
				logger.warn("끝나지 않은 다이제스트를 거두지 못했다 — 다이제스트 {}", id.value(), ex);
			}
		}
	}

	/** 트랜잭션 안에서 — 시작 기한이 지났으면 실패로 끝내고 null, 아니면 새 토큰으로 바꾸고 그 등록 */
	private @Nullable ScheduledDigest recoverScheduled(DigestId id, Instant now) {
		@Nullable Digest digest = this.digests.lock(id.value()).orElse(null);
		if (digest == null || digest.status() != DigestStatus.SCHEDULED
				|| !digest.dispatchedAt().isBefore(now.minus(DigestScheduleRules.DISPATCH_WAIT))) {
			return null;
		}
		if (!digest.createdAt().plus(DigestScheduleRules.START_DEADLINE).isAfter(now)) {
			this.endings.save(digest.abandon(DigestFailure.INTERRUPTED, NOT_STARTED, now));
			return null;
		}
		UUID token = UUID.randomUUID();
		this.digests.save(digest.redispatch(token, now));
		return new ScheduledDigest(id, token);
	}

	/** 트랜잭션 안에서 — 임대가 지난 생성 중인 다이제스트를 실패로 끝낸다 */
	private void interruptRunning(DigestId id, Instant now) {
		@Nullable Digest digest = this.digests.lock(id.value()).orElse(null);
		if (digest == null || digest.status() != DigestStatus.RUNNING) {
			return;
		}
		Instant started = digest.startedAt().orElseThrow();
		if (!started.isBefore(now.minus(DigestScheduleRules.RUN_LEASE))) {
			return;
		}
		this.endings.save(digest.abandon(DigestFailure.INTERRUPTED, NOT_FINISHED, now));
	}

}
