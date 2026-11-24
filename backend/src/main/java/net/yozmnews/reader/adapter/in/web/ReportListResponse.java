package net.yozmnews.reader.adapter.in.web;

import java.util.List;

import net.yozmnews.report.ReportPage;
import org.jspecify.annotations.Nullable;

/**
 * 변화 리포트 목록 한 쪽 — 한 종류의 리포트를 최근 순으로. docs/specs/reader.md §5 reader-R15.
 *
 * @param next 다음 쪽의 {@code before} — 더 없으면 null
 */
record ReportListResponse(List<ReportSummaryResponse> reports, @Nullable Long next) {

	ReportListResponse {
		reports = List.copyOf(reports);
	}

	static ReportListResponse of(ReportPage page) {
		return new ReportListResponse(page.reports().stream().map(ReportSummaryResponse::of).toList(),
				(page.next() == null) ? null : page.next().value());
	}

}
