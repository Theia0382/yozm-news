package net.yozmnews.digest.application;

import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.digest.DigestRequestOutcome;
import net.yozmnews.digest.DigestRequests;
import net.yozmnews.digest.domain.digest.DigestLedger;
import net.yozmnews.digest.domain.digest.DigestOwnerLock;
import net.yozmnews.digest.domain.digest.DigestTrigger;
import net.yozmnews.digest.domain.digest.ScheduledDigest;
import net.yozmnews.digest.domain.schedule.DigestScheduleRules;
import net.yozmnews.digest.domain.schedule.ManualTarget;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.DigestModelDirectory;
import net.yozmnews.user.UserTimezones;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link DigestRequests} 의 구현 — 사용자 잠금 안에서 진행 중 · 오늘 횟수를 보고 행을 만든 뒤, 커밋 뒤 생성 작업을 등록한다.
 * docs/specs/digest.md §4
 * <p>
 * 시간대 · 쓸 모델은 트랜잭션 밖에서 읽는다(쓸 모델이 없으면 세지 않고 거절). 같은 사용자의 수동 요청 · 일정 행 만들기는 사용자 잠금
 * ({@link DigestOwnerLock})으로 줄을 선다 — 확인과 행 만들기 사이에 다른 요청이 끼지 않는다. 대상 날짜 · 기간은 {@link ManualTarget} 이
 * 정하고, 그 읽기와 오늘 횟수는 수동 생성 상태와 함께 쓰는 {@link ManualTargets} 가 한다. 역할 애너테이션이 없다(화면 처리기가 부른다).
 */
@Service
class DigestRequestsService implements DigestRequests {

	private final UserTimezones timezones;

	private final DigestModelDirectory models;

	private final DigestLedger ledger;

	private final ManualTargets targets;

	private final DigestOwnerLock owners;

	private final DigestGeneration generation;

	private final DigestDispatch dispatch;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	DigestRequestsService(UserTimezones timezones, DigestModelDirectory models, DigestLedger ledger,
			DigestOwnerLock owners, DigestGeneration generation, DigestDispatch dispatch,
			TransactionTemplate transaction) {
		this(timezones, models, ledger, owners, generation, dispatch, transaction, InstantSource.system());
	}

	/** 시험이 시계를 넣는다 */
	DigestRequestsService(UserTimezones timezones, DigestModelDirectory models, DigestLedger ledger,
			DigestOwnerLock owners, DigestGeneration generation, DigestDispatch dispatch,
			TransactionTemplate transaction, InstantSource clock) {
		this.timezones = Objects.requireNonNull(timezones, "timezones");
		this.models = Objects.requireNonNull(models, "models");
		this.ledger = Objects.requireNonNull(ledger, "ledger");
		this.targets = new ManualTargets(ledger);
		this.owners = Objects.requireNonNull(owners, "owners");
		this.generation = Objects.requireNonNull(generation, "generation");
		this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
		this.transaction = Objects.requireNonNull(transaction, "transaction");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public DigestRequestOutcome request(UserId user) {
		Objects.requireNonNull(user, "user");
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(DigestDispatch.IN_TRANSACTION);
		}
		Optional<ZoneId> zone = this.timezones.timezoneOf(user);
		Optional<LlmModel> model = this.models.modelOf(user);
		if (zone.isEmpty() || model.isEmpty()) {
			return new DigestRequestOutcome.NoUsableModel();
		}
		Instant now = this.clock.instant();
		Accepted accepted = Objects.requireNonNull(this.transaction.execute(status -> accept(user, zone.get(), now)));
		if (accepted.scheduled() != null) {
			this.dispatch.dispatch(accepted.scheduled());
		}
		return accepted.outcome();
	}

	/** 트랜잭션 안에서 — 사용자 잠금 · 진행 중 · 오늘 횟수 · 대상 · 행 만들기 */
	private Accepted accept(UserId user, ZoneId zone, Instant now) {
		this.owners.lock(user);
		if (this.ledger.inProgress(user)) {
			return new Accepted(new DigestRequestOutcome.InProgress(), null);
		}
		LocalDate today = now.atZone(zone).toLocalDate();
		if (this.targets.manualCountOn(user, today, zone) >= DigestScheduleRules.MANUAL_PER_DAY) {
			return new Accepted(new DigestRequestOutcome.DailyLimitReached(ManualTargets.nextDayStart(today, zone)),
					null);
		}
		ManualTarget target = this.targets.targetOf(user, today, now);
		ScheduledDigest scheduled = this.generation.schedule(user, target.date(), target.period(),
				DigestTrigger.MANUAL);
		return new Accepted(new DigestRequestOutcome.Requested(target.date(), target.regenerated()), scheduled);
	}

	/** 트랜잭션의 결과 — 요청의 결과와, 받았으면 커밋 뒤 등록할 다이제스트 */
	private record Accepted(DigestRequestOutcome outcome, @Nullable ScheduledDigest scheduled) {
	}

}
