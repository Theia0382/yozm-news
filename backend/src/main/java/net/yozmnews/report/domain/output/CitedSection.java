package net.yozmnews.report.domain.output;

import java.util.List;
import java.util.Objects;

import net.yozmnews.report.ReportCitation;
import net.yozmnews.report.ReportSectionKind;

/**
 * 대조를 지난 갈래 섹션 — 종류 · 제목 · 본문 · 인용(대조 순서) · docs/specs/report.md §5 · report-R7
 */
public record CitedSection(ReportSectionKind kind, String title, String body, List<ReportCitation> citations) {

	public CitedSection {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(body, "body");
		citations = List.copyOf(citations);
	}

}
