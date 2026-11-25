package net.yozmnews.report.domain.view;

import java.util.List;
import java.util.Objects;

import net.yozmnews.report.ReportCitation;
import net.yozmnews.report.ReportSectionKind;

/**
 * 저장한 섹션 하나 — 인용은 생성 때의 ID 그대로(위치 순) · docs/specs/report.md §4 · report-R11
 */
public record StoredSection(ReportSectionKind kind, String title, String body, List<ReportCitation> citations) {

	public StoredSection {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(body, "body");
		citations = List.copyOf(citations);
	}

}
