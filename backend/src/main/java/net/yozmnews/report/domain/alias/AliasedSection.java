package net.yozmnews.report.domain.alias;

import java.util.List;
import java.util.Objects;

import net.yozmnews.report.ReportSectionKind;

/**
 * 별칭을 매긴 갈래 — 종류와 그 대상들의 별칭(입력 순서) · docs/specs/report.md §5 · report-R4
 */
public record AliasedSection(ReportSectionKind kind, List<String> aliases) {

	public AliasedSection {
		Objects.requireNonNull(kind, "kind");
		aliases = List.copyOf(aliases);
	}

}
