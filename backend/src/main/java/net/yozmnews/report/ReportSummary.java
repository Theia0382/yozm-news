package net.yozmnews.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 리포트 목록의 한 줄 · docs/specs/report.md §3 · report-R26
 *
 * @param firstDay 기간 [시작, 끝) 의 첫날 — 시작할 때 적은 시간대(시작 전이면 지금 사용자 시간대)의 날짜
 * @param lastDay 기간의 마지막 날 — 끝 바로 앞 순간의 같은 시간대 날짜
 * @param failure 실패 코드 — {@code FAILED} 일 때만
 * @param completedAt 끝낸 시각 — 끝난 상태일 때만
 */
public record ReportSummary(ReportId id, ReportPeriodKind periodKind, Instant periodStart, Instant periodEnd,
		LocalDate firstDay, LocalDate lastDay, ReportStatus status, @Nullable ReportFailure failure, Instant createdAt,
		@Nullable Instant completedAt) {

	/**
	 * @throws IllegalArgumentException 마지막 날이 첫날보다 앞일 때
	 */
	public ReportSummary {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(periodKind, "periodKind");
		Objects.requireNonNull(periodStart, "periodStart");
		Objects.requireNonNull(periodEnd, "periodEnd");
		Objects.requireNonNull(firstDay, "firstDay");
		Objects.requireNonNull(lastDay, "lastDay");
		if (lastDay.isBefore(firstDay)) {
			throw new IllegalArgumentException("기간의 마지막 날이 첫날보다 앞이다: " + firstDay + " ~ " + lastDay);
		}
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(createdAt, "createdAt");
	}

}
