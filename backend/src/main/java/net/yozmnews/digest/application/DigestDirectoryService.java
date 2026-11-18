package net.yozmnews.digest.application;

import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.digest.DigestDates;
import net.yozmnews.digest.DigestDay;
import net.yozmnews.digest.DigestDirectory;
import net.yozmnews.digest.DigestRequestStatus;
import net.yozmnews.digest.DigestSectionView;
import net.yozmnews.digest.DigestUnresolvedFailure;
import net.yozmnews.digest.domain.digest.DigestLedger;
import net.yozmnews.digest.domain.schedule.DigestScheduleRules;
import net.yozmnews.digest.domain.schedule.ManualTarget;
import net.yozmnews.digest.domain.view.DigestDayRepository;
import net.yozmnews.digest.domain.view.DigestDayRow;
import net.yozmnews.digest.domain.view.DigestDayStates;
import net.yozmnews.digest.domain.view.DigestDays;
import net.yozmnews.digest.domain.view.UnresolvedFailures;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.DigestModelDirectory;
import net.yozmnews.user.DigestScheduleEntry;
import net.yozmnews.user.UserDigestSchedules;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공개 조회 {@link DigestDirectory} 의 구현 — docs/specs/digest.md §4 · digest-R38 ~ R40.
 * <p>
 * 날짜 보기 · 날짜 목록은 읽기 전용 트랜잭션 하나다 — 날짜 보기는 행 · 섹션 · 이웃 날짜를 한 스냅숏으로 읽는다. 수동 생성 상태는 일정(시간대 ·
 * 생성 시각) · 쓸 모델을 트랜잭션 밖에서 읽고(수동 생성과 같은 순서), 행 조회는 읽기 전용 트랜잭션 하나에서 {@link ManualTargets} 와 오늘 일정
 * 생성 시각 · 해결되지 않은 마지막 실패의 재료로 읽는다 — 잠그지 않는다. 판정은 {@link DigestScheduleRules#upcomingToday} ·
 * {@link UnresolvedFailures} 가 한다. 역할 애너테이션이 없다(화면 처리기가 부른다).
 */
@Service
class DigestDirectoryService implements DigestDirectory {

	private final DigestLedger ledger;

	private final DigestDayRepository days;

	private final ManualTargets targets;

	private final UserDigestSchedules schedules;

	private final DigestModelDirectory models;

	private final TransactionTemplate readOnly;

	private final InstantSource clock;

	@Autowired
	DigestDirectoryService(DigestLedger ledger, DigestDayRepository days, UserDigestSchedules schedules,
			DigestModelDirectory models, TransactionTemplate transaction) {
		this(ledger, days, schedules, models, transaction, InstantSource.system());
	}

	/** 시험이 시계를 넣는다 */
	DigestDirectoryService(DigestLedger ledger, DigestDayRepository days, UserDigestSchedules schedules,
			DigestModelDirectory models, TransactionTemplate transaction, InstantSource clock) {
		this.ledger = Objects.requireNonNull(ledger, "ledger");
		this.days = Objects.requireNonNull(days, "days");
		this.targets = new ManualTargets(ledger);
		this.schedules = Objects.requireNonNull(schedules, "schedules");
		this.models = Objects.requireNonNull(models, "models");
		this.readOnly = new TransactionTemplate(
				Objects.requireNonNull(Objects.requireNonNull(transaction, "transaction").getTransactionManager()),
				transaction);
		this.readOnly.setReadOnly(true);
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public Optional<DigestDay> latest(UserId user) {
		Objects.requireNonNull(user, "user");
		return Objects.requireNonNull(
				this.readOnly.execute(status -> this.ledger.latestDate(user).flatMap(date -> day(user, date))));
	}

	@Override
	public Optional<DigestDay> on(UserId user, LocalDate date) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(date, "date");
		return Objects.requireNonNull(this.readOnly.execute(status -> day(user, date)));
	}

	@Override
	public DigestDates dates(UserId user, @Nullable LocalDate before, int limit) {
		Objects.requireNonNull(user, "user");
		if (limit < 1 || limit > MAX_DATES) {
			throw new IllegalArgumentException("날짜 목록 한 쪽은 1 ~ " + MAX_DATES + "개다: " + limit);
		}
		return Objects.requireNonNull(this.readOnly
			.execute(status -> DigestDayStates.page(this.days.dates(user, before, limit + 1), limit)));
	}

	@Override
	public Optional<DigestRequestStatus> requestStatus(UserId user) {
		Objects.requireNonNull(user, "user");
		Optional<DigestScheduleEntry> schedule = this.schedules.scheduleOf(user);
		if (schedule.isEmpty()) {
			return Optional.empty();
		}
		ZoneId zone = schedule.get().timezone();
		boolean usableModel = this.models.modelOf(user).isPresent();
		Instant now = this.clock.instant();
		LocalDate today = now.atZone(zone).toLocalDate();
		return Optional.of(Objects.requireNonNull(this.readOnly.execute(status -> {
			int used = this.targets.manualCountOn(user, today, zone);
			ManualTarget target = this.targets.targetOf(user, today, now);
			@Nullable Instant scheduledAt = DigestScheduleRules
				.upcomingToday(now, schedule.get().time(), zone, this.ledger.latestDate(user).orElse(null), usableModel)
				.orElse(null);
			@Nullable DigestUnresolvedFailure unresolved = UnresolvedFailures
				.of(this.days.latestFailed(user), this.days.latestCoveringId(user))
				.map(failed -> new DigestUnresolvedFailure(failed.digestDate(), failed.completedAt()))
				.orElse(null);
			return new DigestRequestStatus(today, zone, usableModel, this.ledger.inProgress(user),
					Math.max(0, DigestScheduleRules.MANUAL_PER_DAY - used), ManualTargets.nextDayStart(today, zone),
					target.date(), target.regenerated(), scheduledAt, unresolved);
		})));
	}

	/** 트랜잭션 안에서 — 그 날짜의 행 · 보이는 다이제스트의 섹션 · 이웃 날짜 */
	private Optional<DigestDay> day(UserId user, LocalDate date) {
		List<DigestDayRow> rows = this.days.rowsOn(user, date);
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		@Nullable DigestDayRow shown = DigestDays.shownRow(rows);
		List<DigestSectionView> sections = (shown == null) ? List.of() : this.days.sectionsOf(shown.id());
		return Optional.of(DigestDays.of(date, rows, sections, this.days.neighbors(user, date)));
	}

}
