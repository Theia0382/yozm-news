package net.yozmnews.report.domain.input;

import java.util.Objects;

import net.yozmnews.report.ReportSectionKind;

/**
 * 직전 리포트의 갈래 섹션 하나 — 제목 · 본문 · docs/specs/report.md §5 · report-R20
 */
public record PriorSection(ReportSectionKind kind, String title, String body) {

	/**
	 * @throws IllegalArgumentException 개요일 때 — 개요는 {@link PriorReport#overview()} 다
	 */
	public PriorSection {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(body, "body");
		if (kind == ReportSectionKind.OVERVIEW) {
			throw new IllegalArgumentException("개요는 갈래 섹션이 아니다");
		}
	}

}
