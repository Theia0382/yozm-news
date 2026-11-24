package net.yozmnews.reader.adapter.in.web;

import java.time.Instant;
import java.time.LocalDate;

import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.ReportSummary;
import org.jspecify.annotations.Nullable;

/**
 * 변화 리포트 한 줄 — 종류 · 기간의 첫날과 마지막 날 · 상태. docs/specs/reader.md §5 reader-R15.
 *
 * @param firstDay 기간의 첫날 — 리포트를 시작할 때의 사용자 시간대(시작 전이면 지금 사용자 시간대)
 * @param lastDay 기간의 마지막 날 — 같은 시간대
 * @param failure 실패 코드 — {@code FAILED} 일 때만. 실패 메시지는 싣지 않는다
 * @param completedAt 끝낸 시각 — 끝난 상태일 때만
 */
record ReportSummaryResponse(long id, ReportPeriodKind kind, LocalDate firstDay, LocalDate lastDay, ReportStatus status,
		@Nullable ReportFailure failure, @Nullable Instant completedAt) {

	static ReportSummaryResponse of(ReportSummary summary) {
		return new ReportSummaryResponse(summary.id().value(), summary.periodKind(), summary.firstDay(),
				summary.lastDay(), summary.status(), summary.failure(), summary.completedAt());
	}

}
