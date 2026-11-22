package net.yozmnews.report;

import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 리포트 목록의 한 줄 · docs/specs/report.md §3
 *
 * @param failure 실패 코드 — {@code FAILED} 일 때만
 * @param completedAt 끝낸 시각 — 끝난 상태일 때만
 */
public record ReportSummary(ReportId id, ReportPeriodKind periodKind, Instant periodStart, Instant periodEnd,
		ReportStatus status, @Nullable ReportFailure failure, Instant createdAt, @Nullable Instant completedAt) {

	public ReportSummary {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(periodKind, "periodKind");
		Objects.requireNonNull(periodStart, "periodStart");
		Objects.requireNonNull(periodEnd, "periodEnd");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(createdAt, "createdAt");
	}

}
