package net.yozmnews.report.domain.output;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.report.ReportCitation;
import net.yozmnews.report.domain.alias.AliasedSection;
import net.yozmnews.report.domain.alias.ReportAliases;

/**
 * 별칭 대조 — 정본 · docs/specs/report.md §5 · report-R7
 * <p>
 * 입력 갈래를 입력 순서대로 대조한다. 칸이 없는 갈래는 섹션이 없다. 별칭은 입력에 있는 것만 대상으로 바꾸고, 한 섹션 안의 같은 대상은 처음 것만 둔다
 * — 다른 갈래의 대상이어도 입력에 있으면 받는다. 모르는 별칭이 있어도 섹션은 버리지 않는다.
 */
public final class ReportCitationCheck {

	private ReportCitationCheck() {
	}

	public static CitedReport check(ReportDraft draft, ReportAliases aliases) {
		Objects.requireNonNull(draft, "draft");
		Objects.requireNonNull(aliases, "aliases");
		List<CitedSection> sections = new ArrayList<>();
		int missing = 0;
		int unknown = 0;
		for (AliasedSection section : aliases.sections()) {
			SectionDraft written = draft.sections().get(section.kind());
			if (written == null) {
				missing++;
				continue;
			}
			Set<ReportCitation> citations = new LinkedHashSet<>();
			for (String ref : written.refs()) {
				Optional<ReportCitation> target = aliases.target(ref);
				if (target.isEmpty()) {
					unknown++;
					continue;
				}
				citations.add(target.get());
			}
			sections.add(new CitedSection(section.kind(), written.title(), written.body(), List.copyOf(citations)));
		}
		return new CitedReport(draft.overview(), sections, missing, unknown);
	}

}
