package net.yozmnews.digest.domain.interest;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.yozmnews.digest.InterestHistorySpan;

/**
 * 관심사 히스토리의 기간 경계 — 정본 — docs/specs/digest.md §5 · digest-R34
 * <p>
 * 사용자 시간대에서 주는 월요일, 월은 1일에 시작한다. 경계는 그날의 시작 시각이다({@link LocalDate#atStartOfDay(ZoneId)} — 자정이 없는
 * 날은 그날의 첫 유효 시각). 부르는 시각을 담은 기간이 마지막이고 진행 중이다.
 */
public final class InterestPeriods {

	private InterestPeriods() {
	}

	/**
	 * @param zone 사용자 시간대
	 * @param span 보일 기간
	 * @param now 부르는 시각
	 * @return 시간 순으로 {@code span.count() + 1} 개 — 맨 앞은 첫 기간의 새로 생긴 관심 · 사라진 관심을 정할 견줌용, 마지막은 진행 중
	 */
	public static List<InterestPeriod> recent(ZoneId zone, InterestHistorySpan span, Instant now) {
		Objects.requireNonNull(zone, "zone");
		Objects.requireNonNull(span, "span");
		Objects.requireNonNull(now, "now");
		LocalDate today = LocalDate.ofInstant(now, zone);
		List<InterestPeriod> periods = new ArrayList<>();
		for (int back = span.count(); back >= 0; back--) {
			LocalDate firstDay = firstDay(span, today, back);
			LocalDate nextFirstDay = firstDay(span, today, back - 1);
			Instant start = firstDay.atStartOfDay(zone).toInstant();
			Instant end = nextFirstDay.atStartOfDay(zone).toInstant();
			boolean inProgress = back == 0;
			periods.add(new InterestPeriod(firstDay, nextFirstDay.minusDays(1), start, end, inProgress ? now : end,
					inProgress));
		}
		return List.copyOf(periods);
	}

	/** 부르는 날을 담은 기간에서 {@code back} 개 앞 기간의 첫날 — {@code back} 이 −1 이면 다음 기간의 첫날 */
	private static LocalDate firstDay(InterestHistorySpan span, LocalDate today, int back) {
		return switch (span.unit()) {
			case WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(back);
			case MONTH -> today.withDayOfMonth(1).minusMonths(back);
		};
	}

}
