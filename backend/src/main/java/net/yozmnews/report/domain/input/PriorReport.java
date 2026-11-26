package net.yozmnews.report.domain.input;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPeriodKind;

/**
 * 직전 리포트 글 — 직전 리포트 또는 월간에 싣는 주간 리포트의 보이는 글 · docs/specs/report.md §5 · report-R20
 *
 * @param sections 보이는 갈래 섹션 — 위치 순
 */
public record PriorReport(ReportId id, ReportPeriodKind kind, Instant start, Instant end, List<PriorSection> sections) {

	public PriorReport {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");
		sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
	}

}
