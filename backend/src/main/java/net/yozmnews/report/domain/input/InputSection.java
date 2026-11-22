package net.yozmnews.report.domain.input;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import net.yozmnews.report.ReportCitation;
import net.yozmnews.report.ReportSectionKind;

/**
 * 생성의 입력의 갈래 하나 — 종류와 인용 대상 · docs/specs/report.md §5 · report-R3
 * <p>
 * 갈래 내용(재료)은 재료를 만드는 작업이 더한다. 인용 대상이 없어도 된다(통계만의 갈래).
 */
public record InputSection(ReportSectionKind kind, List<ReportCitation> targets) {

	/**
	 * @throws IllegalArgumentException 종류가 개요이거나 같은 대상이 두 번 있을 때
	 */
	public InputSection {
		Objects.requireNonNull(kind, "kind");
		targets = List.copyOf(targets);
		if (kind == ReportSectionKind.OVERVIEW) {
			throw new IllegalArgumentException("개요는 입력 갈래가 아니다");
		}
		if (new HashSet<>(targets).size() != targets.size()) {
			throw new IllegalArgumentException("한 갈래에 같은 대상이 두 번 있다: " + kind);
		}
	}

}
