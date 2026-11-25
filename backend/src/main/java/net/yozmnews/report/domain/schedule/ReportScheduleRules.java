package net.yozmnews.report.domain.schedule;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.domain.report.ReportLedger;
import net.yozmnews.report.domain.report.ReportPeriod;
import org.jspecify.annotations.Nullable;

/**
 * 리포트 일정 규칙과 상수의 정본 — 지금 만들 주간 · 월간 기간, 생성 조건, 월간이 기다릴지, 거두기 기준, 다시 만들기 상한.
 * docs/specs/report.md §5
 * <p>
 * 기간의 경계는 사용자 시간대에서 고른 요일(주간) · 고른 날짜(월간)의 다이제스트 생성 시각이고, 기간 끝이 곧 생성 시각이다. 일정은 경계가 지난 뒤
 * {@link #CATCH_UP} 안에서만 그 기간을 만든다. 설정을 바꾸면 새 설정의 온전한 기간을 만든다 — 끝이 그 종류의 가장 늦은 리포트의 끝보다 뒤일 때만
 * (겹침 · 틈은 둔다). 값은 측정 전 목표다.
 */
public final class ReportScheduleRules {

	/** 경계가 지난 뒤 따라잡아 만드는 창 — 지나면 그 기간은 만들지 않는다 */
	public static final Duration CATCH_UP = Duration.ofHours(2);

	/** 등록한 생성 작업이 시작되기를 기다리는 시간 — 지나도 생성을 기다리면 새 토큰으로 다시 등록한다 */
	public static final Duration DISPATCH_WAIT = Duration.ofMinutes(15);

	/** 생성을 기다림이 된 리포트가 시작될 기한 — 지나도 생성을 기다리면 다시 등록하지 않고 끝나지 않은 실패로 끝낸다 */
	public static final Duration START_DEADLINE = Duration.ofHours(1);

	/**
	 * 생성 중인 리포트의 임대 — 시작한 지 이만큼 지나도 끝나지 않으면 멈춘 생성으로 보고 끝나지 않은 실패로 끝낸다. 생성 호출 둘(읽지 못하거나 빠진
	 * 갈래가 있으면 한 번 다시 묻는다 — report-R13)의 가장 긴 시간과 재료 조립 뒤 트랜잭션의 여유 10분이다
	 */
	public static final Duration RUN_LEASE = LlmClientFactory.LONGEST_GENERATION_CALL.multipliedBy(2)
		.plus(Duration.ofMinutes(10));

	/** 주간 리포트를 만드는 데 필요한 그 주의 READY 다이제스트 날짜 수 */
	public static final int MIN_DIGEST_DAYS = 3;

	/** 월간 리포트를 만드는 데 필요한 그 기간 안에 다 든 READY 주간 리포트 수 */
	public static final int MIN_WEEKLY_REPORTS = 2;

	/** 실패한 리포트 하나를 다시 만들 수 있는 횟수 */
	public static final int MAX_REGENERATIONS = 3;

	private ReportScheduleRules() {
	}

	/**
	 * 그 시간대에서 그 날짜 · 시각의 순간 — 기간의 경계. 없는 시각(서머타임의 틈)은 틈의 길이만큼 뒤로, 두 번 있는 시각(겹침)은 앞 오프셋이다
	 * (java.time 의 규칙 — 다이제스트의 생성 시각과 같은 규칙이라 리포트 경계와 그날 다이제스트의 기간 끝이 같은 순간이다).
	 */
	public static Instant boundary(LocalDate date, LocalTime time, ZoneId zone) {
		return ZonedDateTime.of(date, time, zone).toInstant();
	}

	/**
	 * 지금 만들 주간 기간 — 고른 요일의 경계 가운데 지금 이전의 가장 늦은 것 E 가 {@code now < E + CATCH_UP} 이고 그 종류의 가장 늦은 리포트
	 * 끝보다 뒤일 때 [E 의 7일 전 경계, E).
	 * @param latestEnd 그 사용자의 주간 리포트 가운데 가장 늦은 끝(상태 무관) — 없으면 null
	 */
	public static Optional<ReportPeriod> dueWeekly(Instant now, ZoneId zone, LocalTime time, DayOfWeek day,
			@Nullable Instant latestEnd) {
		Objects.requireNonNull(now, "now");
		Objects.requireNonNull(zone, "zone");
		Objects.requireNonNull(time, "time");
		Objects.requireNonNull(day, "day");
		LocalDate date = now.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(day));
		if (boundary(date, time, zone).isAfter(now)) {
			date = date.minusWeeks(1);
		}
		return due(now, ReportPeriodKind.WEEK, boundary(date.minusWeeks(1), time, zone), boundary(date, time, zone),
				latestEnd);
	}

	/**
	 * 지금 만들 월간 기간 — 고른 날짜(그 날이 없는 달은 그 달 마지막 날 — {@link #monthlyDate})의 경계 가운데 지금 이전의 가장 늦은 것 E 가
	 * {@code now < E + CATCH_UP} 이고 그 종류의 가장 늦은 리포트 끝보다 뒤일 때 [전 달의 경계, E). 주간 리포트를 받지 않으면 월간도 없다.
	 * @param latestEnd 그 사용자의 월간 리포트 가운데 가장 늦은 끝(상태 무관) — 없으면 null
	 */
	public static Optional<ReportPeriod> dueMonthly(Instant now, ZoneId zone, LocalTime time, boolean weekly,
			boolean monthly, int day, @Nullable Instant latestEnd) {
		Objects.requireNonNull(now, "now");
		Objects.requireNonNull(zone, "zone");
		Objects.requireNonNull(time, "time");
		if (!weekly || !monthly) {
			return Optional.empty();
		}
		YearMonth month = YearMonth.from(now.atZone(zone));
		if (boundary(monthlyDate(month, day), time, zone).isAfter(now)) {
			month = month.minusMonths(1);
		}
		return due(now, ReportPeriodKind.MONTH, boundary(monthlyDate(month.minusMonths(1), day), time, zone),
				boundary(monthlyDate(month, day), time, zone), latestEnd);
	}

	/** 그 달의 월간 경계 날짜 — 고른 날, 그 날이 없는 달은 그 달 마지막 날 */
	public static LocalDate monthlyDate(YearMonth month, int day) {
		Objects.requireNonNull(month, "month");
		if (day < 1) {
			throw new IllegalArgumentException("날짜는 1 이상이다: " + day);
		}
		return month.atDay(Math.min(day, month.lengthOfMonth()));
	}

	/** 실패한 리포트를 더 다시 만들 수 있는 횟수 — {@link #MAX_REGENERATIONS} 에서 다시 만든 횟수를 뺀 값(0 아래로 내려가지 않는다) */
	public static int regenerationsLeft(int regenerations) {
		return Math.max(0, MAX_REGENERATIONS - regenerations);
	}

	/** 주간 리포트의 생성 조건 — 그 주에 READY 다이제스트가 {@link #MIN_DIGEST_DAYS} 날 이상 */
	public static boolean enoughDigestDays(int readyDigestDays) {
		return readyDigestDays >= MIN_DIGEST_DAYS;
	}

	/** 주간 리포트가 월간 기간 안에 다 드는가 — 월간이 기다리고 세고 싣는 주간 */
	public static boolean within(ReportPeriod weekly, ReportPeriod monthly) {
		Objects.requireNonNull(weekly, "weekly");
		Objects.requireNonNull(monthly, "monthly");
		return !weekly.start().isBefore(monthly.start()) && !weekly.end().isAfter(monthly.end());
	}

	/** 월간이 기다리고 세는 주간 — 주간 리포트 가운데 월간 기간 안에 다 든 것({@link #within}), 순서는 그대로 */
	public static List<ReportLedger.Weekly> monthlyWeeklies(Collection<ReportLedger.Weekly> weeklies,
			ReportPeriod monthly) {
		Objects.requireNonNull(weeklies, "weeklies");
		Objects.requireNonNull(monthly, "monthly");
		return weeklies.stream().filter(weekly -> within(weekly.period(), monthly)).toList();
	}

	/** 월간이 입력으로 싣는 주간 — 월간 기간 안에 다 든 주간 가운데 {@link #countsForMonthly} 인 것, 순서는 그대로 */
	public static List<ReportId> monthlyInputs(Collection<ReportLedger.Weekly> weeklies, ReportPeriod monthly) {
		return monthlyWeeklies(weeklies, monthly).stream()
			.filter(weekly -> countsForMonthly(weekly.status()))
			.map(ReportLedger.Weekly::id)
			.toList();
	}

	/**
	 * 월간을 지금 만들지 — 그 월간 기간 안에 다 든 주간 리포트의 상태로 정한다. 끝나지 않은 것(생성을 기다림 · 생성 중)이 있으면 기다리고, 아니면
	 * 월간이 세는 주간({@link #countsForMonthly})이 {@link #MIN_WEEKLY_REPORTS} 편 이상일 때만 만든다.
	 */
	public static MonthlyReadiness monthlyReadiness(Collection<ReportStatus> weeklies) {
		Objects.requireNonNull(weeklies, "weeklies");
		if (weeklies.stream().anyMatch(status -> !status.finished())) {
			return MonthlyReadiness.WAIT;
		}
		long counted = weeklies.stream().filter(ReportScheduleRules::countsForMonthly).count();
		return (counted >= MIN_WEEKLY_REPORTS) ? MonthlyReadiness.MAKE : MonthlyReadiness.SKIP;
	}

	/** 끝난 주간 리포트 가운데 월간이 세고 싣는 것인가 — 내용 있음만(실패 · 재료 없음은 세지도 싣지도 않는다) */
	public static boolean countsForMonthly(ReportStatus weekly) {
		return switch (weekly) {
			case READY -> true;
			case SCHEDULED, RUNNING, EMPTY, FAILED -> false;
		};
	}

	private static Optional<ReportPeriod> due(Instant now, ReportPeriodKind kind, Instant start, Instant end,
			@Nullable Instant latestEnd) {
		boolean inWindow = !now.isBefore(end) && now.isBefore(end.plus(CATCH_UP));
		boolean after = latestEnd == null || end.isAfter(latestEnd);
		return (inWindow && after) ? Optional.of(new ReportPeriod(kind, start, end)) : Optional.empty();
	}

	/** 월간을 지금 만들지 */
	public enum MonthlyReadiness {

		/** 끝나지 않은 주간이 있다 — 다음 회차에 다시 본다 */
		WAIT,

		/** 만든다 */
		MAKE,

		/** 주간 READY 가 모자라다 — 만들지 않는다 */
		SKIP

	}

}
