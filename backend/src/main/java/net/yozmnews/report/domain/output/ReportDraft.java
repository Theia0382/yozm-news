package net.yozmnews.report.domain.output;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.report.ReportSectionKind;

/**
 * 읽은 출력 — 개요와 갈래마다의 칸(출력에 있는 갈래만) · docs/specs/report.md §5 · report-R5
 */
public record ReportDraft(String overview, Map<ReportSectionKind, SectionDraft> sections) {

	public ReportDraft {
		Objects.requireNonNull(overview, "overview");
		sections = sections.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(sections));
	}

}
