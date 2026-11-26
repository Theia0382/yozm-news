package net.yozmnews.report.domain.input;

import java.util.Objects;

import net.yozmnews.report.ReportSectionKind;

/**
 * 직전 리포트의 갈래 섹션 하나 — 제목 · 본문 · docs/specs/report.md §5 · report-R20
 */
public record PriorSection(ReportSectionKind kind, String title, String body) {

	public PriorSection {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(body, "body");
	}

}
