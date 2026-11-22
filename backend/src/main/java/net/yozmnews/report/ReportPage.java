package net.yozmnews.report;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 리포트 목록 한 쪽 · docs/specs/report.md §3 · report-R11
 *
 * @param next 다음 쪽의 {@code before} — 이 쪽의 마지막 ID. 더 없으면 {@code null}
 */
public record ReportPage(List<ReportSummary> reports, @Nullable ReportId next) {

	public ReportPage {
		reports = List.copyOf(reports);
	}

}
