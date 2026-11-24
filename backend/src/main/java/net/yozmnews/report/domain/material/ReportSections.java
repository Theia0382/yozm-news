package net.yozmnews.report.domain.material;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.report.domain.input.CoverageSection;
import net.yozmnews.report.domain.input.FlowMaterial;
import net.yozmnews.report.domain.input.InputSection;
import net.yozmnews.report.domain.input.InterestsSection;
import net.yozmnews.report.domain.input.StorylinesSection;
import net.yozmnews.report.domain.input.TopicCoverageLine;

/**
 * 갈래 조건과 갈래 순서의 정본 · docs/specs/report.md §5 · report-R14
 * <p>
 * 재료가 있는 갈래만 {@code STORYLINES} · {@code COVERAGE} · {@code INTERESTS} 순서로 든다. 재료가 하나도 없으면 빈 목록이다 —
 * 생성이 {@code EMPTY} 로 끝낸다(report-R13).
 */
public final class ReportSections {

	private ReportSections() {
	}

	public static List<InputSection> of(List<FlowMaterial> flows, List<TopicCoverageLine> topics,
			InterestLines.Lines interests) {
		List<InputSection> sections = new ArrayList<>();
		if (!flows.isEmpty()) {
			sections.add(new StorylinesSection(flows));
		}
		if (!topics.isEmpty()) {
			sections.add(new CoverageSection(topics));
		}
		if (!interests.topics().isEmpty() || !interests.flows().isEmpty() || !interests.changes().isEmpty()) {
			sections.add(new InterestsSection(interests.topics(), interests.flows(), interests.changes()));
		}
		return List.copyOf(sections);
	}

}
