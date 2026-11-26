package net.yozmnews.report.domain.output;

import java.util.List;

/**
 * 대조를 지난 출력 · docs/specs/report.md §5 · report-R7
 *
 * @param sections 갈래 섹션 — 입력 순서, 출력에 칸이 있던 갈래만
 * @param missingSections 출력에 칸이 없던 입력 갈래의 수
 * @param unknownRefs 버린 별칭(입력에 없는 별칭 · 빈 별칭)의 수
 */
public record CitedReport(List<CitedSection> sections, int missingSections, int unknownRefs) {

	public CitedReport {
		sections = List.copyOf(sections);
		if (missingSections < 0 || unknownRefs < 0) {
			throw new IllegalArgumentException("수가 음수다: " + missingSections + ", " + unknownRefs);
		}
	}

}
