package net.yozmnews.report.domain.input;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.report.ReportCitation;
import net.yozmnews.report.ReportSectionKind;

/**
 * 추적한 흐름의 변화 갈래(`STORYLINES`) — 흐름마다 흐름 재료 · docs/specs/report.md §5 · report-R14 · report-R16
 *
 * @param flows 흐름 — 헤드 ID 순. 비지 않는다
 */
public record StorylinesSection(List<FlowMaterial> flows) implements InputSection {

	/**
	 * @throws IllegalArgumentException 흐름이 없을 때 — 재료가 없는 갈래는 입력에 들지 않는다
	 */
	public StorylinesSection {
		flows = List.copyOf(Objects.requireNonNull(flows, "flows"));
		if (flows.isEmpty()) {
			throw new IllegalArgumentException("추적한 흐름의 갈래에 흐름이 없다");
		}
	}

	@Override
	public ReportSectionKind kind() {
		return ReportSectionKind.STORYLINES;
	}

	/** 흐름마다 헤드, 이어 그 흐름의 사건 줄의 사건(report-R14) */
	@Override
	public List<ReportCitation> targets() {
		Set<ReportCitation> targets = new LinkedHashSet<>();
		for (FlowMaterial flow : this.flows) {
			targets.add(new ReportCitation.Storyline(flow.head()));
			flow.events().forEach(line -> targets.add(new ReportCitation.Event(line.event())));
		}
		return List.copyOf(new ArrayList<>(targets));
	}

}
