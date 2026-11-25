package net.yozmnews.report.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.digest.ReadyDigestDays;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.domain.report.ReportLedger;
import net.yozmnews.report.domain.report.ReportOwnerLock;
import net.yozmnews.report.domain.report.ReportPeriod;
import net.yozmnews.report.domain.report.ScheduledReport;
import net.yozmnews.report.domain.schedule.ReportScheduleRules;
import net.yozmnews.report.domain.schedule.ReportScheduleRules.MonthlyReadiness;
import net.yozmnews.user.ReportModelDirectory;
import net.yozmnews.user.ReportScheduleDirectory;
import net.yozmnews.user.ReportScheduleEntry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 일정 회차 — 리포트 시점이 된 사용자의 주간 · 월간 리포트 행을 만들고 커밋 뒤 생성 작업을 등록한다. docs/specs/report.md §4
 * <p>
 * 일정 목록을 쪽으로 읽어 기간 판정({@link ReportScheduleRules})으로 먼저 거르고, 생성 조건(주간 — 그 주의 READY 다이제스트 날짜 수, 월간 —
 * 기간 안에 다 든 주간의 상태)을 본 뒤 쓸 모델이 있는 사용자만 행을 만든다. 같은 사용자의 주간을 월간보다 먼저 본다 — 같은 회차에 만든 주간은
 * 월간이 끝나지 않은 주간으로 보고 다음 회차로 미룬다. 행 만들기는 사용자마다 트랜잭션 하나에서 사용자 잠금을 잡고 그 종류의 가장 늦은 끝을 다시
 * 확인한 뒤 한다 — 같은 사용자 · 종류 · 기간은 저장소의 유일 제약이 한 번 더 막는다. 사용자 하나의 실패(주간 · 월간 함께)는 로그를 남기고 다음
 * 사용자로 넘어간다 — 다음 회차가 다시 본다. 역할 애너테이션이 없다(부르는 반복 작업이 작업 역할이다).
 */
@Service
public class ReportScheduling {

	private static final Logger logger = LoggerFactory.getLogger(ReportScheduling.class);

	private final ReportScheduleDirectory schedules;

	private final ReportModelDirectory models;

	private final ReadyDigestDays digestDays;

	private final ReportLedger ledger;

	private final ReportOwnerLock owners;

	private final ReportGeneration generation;

	private final ReportDispatch dispatch;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	ReportScheduling(ReportScheduleDirectory schedules, ReportModelDirectory models, ReadyDigestDays digestDays,
			ReportLedger ledger, ReportOwnerLock owners, ReportGeneration generation, ReportDispatch dispatch,
			TransactionTemplate transaction, InstantSource clock) {
		this.schedules = Objects.requireNonNull(schedules, "schedules");
		this.models = Objects.requireNonNull(models, "models");
		this.digestDays = Objects.requireNonNull(digestDays, "digestDays");
		this.ledger = Objects.requireNonNull(ledger, "ledger");
		this.owners = Objects.requireNonNull(owners, "owners");
		this.generation = Objects.requireNonNull(generation, "generation");
		this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
		this.transaction = Objects.requireNonNull(transaction, "transaction");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * 회차 하나 — 지금 시각 하나로 일정 목록 전체를 본다.
	 * @return 이 회차가 만든 리포트 수
	 */
	public int runRound() {
		Instant now = this.clock.instant();
		int created = 0;
		long after = 0;
		while (true) {
			List<ReportScheduleEntry> page = this.schedules.page(after, ReportScheduleDirectory.MAX_PAGE);
			if (page.isEmpty()) {
				break;
			}
			List<UserId> users = page.stream().map(ReportScheduleEntry::user).toList();
			Map<UserId, Instant> weeklyEnds = this.ledger.latestEnds(users, ReportPeriodKind.WEEK);
			Map<UserId, Instant> monthlyEnds = this.ledger.latestEnds(users, ReportPeriodKind.MONTH);
			for (ReportScheduleEntry entry : page) {
				try {
					created += scheduleUser(entry, now, weeklyEnds.get(entry.user()), monthlyEnds.get(entry.user()));
				}
				catch (RuntimeException ex) {
					logger.warn("일정 리포트를 만들지 못했다 — 사용자 {}", entry.user().value(), ex);
				}
			}
			after = page.getLast().user().value();
			if (page.size() < ReportScheduleDirectory.MAX_PAGE) {
				break;
			}
		}
		return created;
	}

	/** 사용자 하나 — 주간 먼저, 이어서 월간. 만든 수 */
	private int scheduleUser(ReportScheduleEntry entry, Instant now, @Nullable Instant weeklyEnd,
			@Nullable Instant monthlyEnd) {
		int created = 0;
		Optional<ReportPeriod> weekly = entry.weekly()
				? ReportScheduleRules.dueWeekly(now, entry.timezone(), entry.time(), entry.weeklyDay(), weeklyEnd)
				: Optional.empty();
		if (weekly.isPresent() && ReportScheduleRules.enoughDigestDays(
				this.digestDays.count(entry.user(), weekly.get().start(), weekly.get().end()))) {
			created += make(entry.user(), weekly.get());
		}
		Optional<ReportPeriod> monthly = ReportScheduleRules.dueMonthly(now, entry.timezone(), entry.time(),
				entry.weekly(), entry.monthly(), entry.monthlyDay(), monthlyEnd);
		if (monthly.isPresent() && ReportScheduleRules.monthlyReadiness(ReportScheduleRules
			.monthlyWeeklies(this.ledger.weekliesOverlapping(entry.user(), monthly.get().start(), monthly.get().end()),
					monthly.get())
			.stream()
			.map(ReportLedger.Weekly::status)
			.toList()) == MonthlyReadiness.MAKE) {
			created += make(entry.user(), monthly.get());
		}
		return created;
	}

	/** 쓸 모델이 있으면 행을 만들고 커밋 뒤 등록한다. 만든 수 */
	private int make(UserId user, ReportPeriod period) {
		if (this.models.modelOf(user).isEmpty()) {
			return 0;
		}
		@Nullable ScheduledReport scheduled = this.transaction.execute(status -> create(user, period));
		if (scheduled == null) {
			return 0;
		}
		this.dispatch.dispatch(scheduled);
		return 1;
	}

	/** 트랜잭션 안에서 — 사용자 잠금 · 가장 늦은 끝 다시 확인 · 행 만들기. 만들지 않으면 null */
	private @Nullable ScheduledReport create(UserId user, ReportPeriod period) {
		this.owners.lock(user);
		Optional<Instant> latest = this.ledger.latestEnd(user, period.kind());
		if (latest.isPresent() && !period.end().isAfter(latest.get())) {
			return null;
		}
		return this.generation.schedule(user, period);
	}

}
