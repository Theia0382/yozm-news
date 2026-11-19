package net.yozmnews.digest.application;

import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.digest.domain.digest.DigestLedger;
import net.yozmnews.digest.domain.digest.DigestOwnerLock;
import net.yozmnews.digest.domain.digest.DigestPeriod;
import net.yozmnews.digest.domain.digest.DigestTrigger;
import net.yozmnews.digest.domain.digest.ScheduledDigest;
import net.yozmnews.digest.domain.schedule.DigestPeriods;
import net.yozmnews.digest.domain.schedule.DigestScheduleRules;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.DigestModelDirectory;
import net.yozmnews.user.DigestScheduleDirectory;
import net.yozmnews.user.DigestScheduleEntry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 일정 회차 — 생성 시각이 된 사용자의 다이제스트 행을 만들고 커밋 뒤 생성 작업을 등록한다. docs/specs/digest.md §4
 * <p>
 * 일정 목록을 쪽으로 읽어 날짜 판정({@link DigestScheduleRules#dueDate})으로 먼저 거르고, 맞는 사용자만 쓸 모델을 묻는다 — 쓸 모델이 없으면
 * 행을 만들지 않는다. 행 만들기는 사용자마다 트랜잭션 하나에서 사용자 잠금을 잡고 가장 늦은 날짜를 다시 확인한 뒤 한다. 기간 끝은 생성
 * 시각이고, 기간이 비면 그 날짜를 건너뛴다. 사용자 하나의 실패는 로그를 남기고 다음 사용자로 넘어간다 — 다음 회차가 다시 본다. 역할
 * 애너테이션이 없다(부르는 반복 작업이 작업 역할이다).
 */
@Service
public class DigestScheduling {

	private static final Logger logger = LoggerFactory.getLogger(DigestScheduling.class);

	private final DigestScheduleDirectory schedules;

	private final DigestModelDirectory models;

	private final DigestLedger ledger;

	private final DigestOwnerLock owners;

	private final DigestGeneration generation;

	private final DigestDispatch dispatch;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	DigestScheduling(DigestScheduleDirectory schedules, DigestModelDirectory models, DigestLedger ledger,
			DigestOwnerLock owners, DigestGeneration generation, DigestDispatch dispatch,
			TransactionTemplate transaction, InstantSource clock) {
		this.schedules = Objects.requireNonNull(schedules, "schedules");
		this.models = Objects.requireNonNull(models, "models");
		this.ledger = Objects.requireNonNull(ledger, "ledger");
		this.owners = Objects.requireNonNull(owners, "owners");
		this.generation = Objects.requireNonNull(generation, "generation");
		this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
		this.transaction = Objects.requireNonNull(transaction, "transaction");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * 회차 하나 — 지금 시각 하나로 일정 목록 전체를 본다.
	 * @return 이 회차가 만든 다이제스트 수
	 */
	public int runRound() {
		Instant now = this.clock.instant();
		int created = 0;
		long after = 0;
		while (true) {
			List<DigestScheduleEntry> page = this.schedules.page(after, DigestScheduleDirectory.MAX_PAGE);
			if (page.isEmpty()) {
				break;
			}
			Map<UserId, LocalDate> latest = this.ledger.latestDates(page.stream().map(DigestScheduleEntry::user).toList());
			for (DigestScheduleEntry entry : page) {
				Optional<LocalDate> due = DigestScheduleRules.dueDate(now, entry.time(), entry.timezone(),
						latest.get(entry.user()));
				if (due.isEmpty()) {
					continue;
				}
				try {
					if (scheduleOne(entry, due.get())) {
						created++;
					}
				}
				catch (RuntimeException ex) {
					logger.warn("일정 다이제스트를 만들지 못했다 — 사용자 {}, 날짜 {}", entry.user().value(), due.get(), ex);
				}
			}
			after = page.getLast().user().value();
			if (page.size() < DigestScheduleDirectory.MAX_PAGE) {
				break;
			}
		}
		return created;
	}

	/** 사용자 하나 — 쓸 모델이 있으면 행을 만들고 커밋 뒤 등록한다. 만들었으면 참 */
	private boolean scheduleOne(DigestScheduleEntry entry, LocalDate date) {
		Optional<LlmModel> model = this.models.modelOf(entry.user());
		if (model.isEmpty()) {
			return false;
		}
		Instant end = DigestScheduleRules.generationTime(date, entry.time(), entry.timezone());
		// 같은 날짜의 일정 다이제스트는 사용자 잠금 안의 다시 확인이 막고, 저장소의 유일 제약이 한 번 더 막는다(어기면 예외 — 회차가 로그를 남긴다)
		@Nullable ScheduledDigest scheduled = this.transaction.execute(status -> create(entry.user(), date, end));
		if (scheduled == null) {
			return false;
		}
		this.dispatch.dispatch(scheduled);
		return true;
	}

	/** 트랜잭션 안에서 — 사용자 잠금 · 가장 늦은 날짜 다시 확인 · 기간 · 행 만들기. 만들지 않으면 null */
	private @Nullable ScheduledDigest create(UserId user, LocalDate date, Instant end) {
		this.owners.lock(user);
		Optional<LocalDate> latest = this.ledger.latestDate(user);
		if (latest.isPresent() && !date.isAfter(latest.get())) {
			return null;
		}
		Optional<DigestPeriod> period = DigestPeriods.next(end, this.ledger.lastCoveredEnd(user).orElse(null));
		if (period.isEmpty()) {
			return null;
		}
		return this.generation.schedule(user, date, period.get(), DigestTrigger.SCHEDULED);
	}

}
