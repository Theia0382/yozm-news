package net.yozmnews.report.application;

import java.time.InstantSource;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportRegenerationOutcome;
import net.yozmnews.report.ReportRequests;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.domain.report.Report;
import net.yozmnews.report.domain.report.ReportRepository;
import net.yozmnews.report.domain.report.ScheduledReport;
import net.yozmnews.report.domain.schedule.ReportScheduleRules;
import net.yozmnews.user.ReportModelDirectory;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link ReportRequests} 의 구현 — 실패한 리포트를 같은 행으로 되돌려 다시 생성을 기다리게 한다. docs/specs/report.md §4
 * <p>
 * 트랜잭션 하나에서 행을 잠그고 그 사용자의 것인지 · 실패인지 · 횟수가 남았는지 · 쓸 모델이 있는지 본 뒤, 되돌린 행을 저장하고 그 뒤에 시작의
 * 기록(스냅숏 · 직전 리포트 · 시간대)을 비운다(테이블 제약 — 시작한 행만 스냅숏이 있다). 생성 작업은 커밋 뒤 새 토큰으로 등록한다. 역할
 * 애너테이션이 없다(화면 역할의 처리기가 부르고 등록은 두 역할 공통이다).
 */
@Service
class ReportRequestsService implements ReportRequests {

	/** 트랜잭션 안에서 불렀을 때의 예외 메시지 */
	static final String IN_TRANSACTION = "다시 만들기를 트랜잭션 안에서 불렀다 — 커밋 뒤 생성 작업을 등록한다";

	private final ReportRepository reports;

	private final ReportModelDirectory models;

	private final ReportDispatch dispatch;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	ReportRequestsService(ReportRepository reports, ReportModelDirectory models, ReportDispatch dispatch,
			TransactionTemplate transaction, InstantSource clock) {
		this.reports = Objects.requireNonNull(reports, "reports");
		this.models = Objects.requireNonNull(models, "models");
		this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
		this.transaction = Objects.requireNonNull(transaction, "transaction");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public Optional<ReportRegenerationOutcome> regenerate(UserId user, ReportId id) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(id, "id");
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(IN_TRANSACTION);
		}
		Decision decision = Objects.requireNonNull(this.transaction.execute(status -> decide(user, id)));
		if (decision.scheduled() != null) {
			this.dispatch.dispatch(decision.scheduled());
		}
		return decision.outcome();
	}

	/** 트랜잭션 안에서 — 판정하고 받았으면 행을 되돌린다 */
	private Decision decide(UserId user, ReportId id) {
		Optional<Report> locked = this.reports.lock(id.value()).filter(report -> report.userId().equals(user));
		if (locked.isEmpty()) {
			return new Decision(Optional.empty(), null);
		}
		Report report = locked.get();
		if (report.status() != ReportStatus.FAILED) {
			return new Decision(Optional.of(new ReportRegenerationOutcome.NotFailed(report.status())), null);
		}
		if (ReportScheduleRules.regenerationsLeft(report.regenerations()) == 0) {
			return new Decision(Optional.of(new ReportRegenerationOutcome.LimitReached()), null);
		}
		if (this.models.modelOf(user).isEmpty()) {
			return new Decision(Optional.of(new ReportRegenerationOutcome.NoUsableModel()), null);
		}
		UUID token = UUID.randomUUID();
		this.reports.save(report.regenerate(token, this.clock.instant()));
		this.reports.clearStart(id.value());
		return new Decision(Optional.of(new ReportRegenerationOutcome.Requested()), new ScheduledReport(id, token));
	}

	/**
	 * 트랜잭션 안의 판정 — 결과와, 받았으면 커밋 뒤 등록할 생성 작업.
	 * @param outcome 결과 — 그 사용자의 리포트가 아니면 빈 값
	 * @param scheduled 받았을 때의 등록 — 아니면 null
	 */
	private record Decision(Optional<ReportRegenerationOutcome> outcome,
			@Nullable ScheduledReport scheduled) {
	}

}
