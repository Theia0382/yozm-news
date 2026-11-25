package net.yozmnews.report.domain.report;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

import net.yozmnews.report.ReportPeriodKind;

/**
 * 리포트 기간 — 종류와 [시작, 끝) · docs/specs/report.md §5 · report-R1 · report-R26
 * <p>
 * 기간을 날짜로 적는 규칙의 정본이다 — 첫날은 시작 순간의 날짜, 마지막 날은 끝 바로 앞 순간의 날짜다(끝은 기간에 들지 않는다).
 */
public record ReportPeriod(ReportPeriodKind kind, Instant start, Instant end) {

	/** 사용자 시간대를 얻지 못할 때(계정이 없다) 기간 날짜를 적는 시간대 — 정본 */
	public static final ZoneId NO_ACCOUNT_ZONE = ZoneId.of("UTC");

	/**
	 * @throws IllegalArgumentException 끝이 시작보다 뒤가 아닐 때
	 */
	public ReportPeriod {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");
		if (!end.isAfter(start)) {
			throw new IllegalArgumentException("기간의 끝이 시작보다 뒤가 아니다: " + start + " ~ " + end);
		}
	}

	/** 기간 [시작, 끝) 의 첫날 — 시작 순간의 그 시간대 날짜 */
	public static LocalDate firstDay(Instant start, ZoneId zone) {
		return start.atZone(zone).toLocalDate();
	}

	/** 기간 [시작, 끝) 의 마지막 날 — 끝 바로 앞 순간의 그 시간대 날짜 */
	public static LocalDate lastDay(Instant end, ZoneId zone) {
		return end.minusNanos(1).atZone(zone).toLocalDate();
	}

}
