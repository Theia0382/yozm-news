package net.yozmnews.reader.adapter.in.web;

import java.util.Objects;

import net.yozmnews.report.InterestShift;
import net.yozmnews.report.ReportSectionStats;
import org.jspecify.annotations.Nullable;

/**
 * 흐름 하나의 관심 — 기간 시작 · 끝의 관심 값 · 변화. docs/specs/reader.md §5 reader-R16.
 *
 * @param start 기간 시작의 관심 값 — 신호가 없으면 null
 * @param end 기간 끝의 관심 값 — 신호가 없으면 null
 */
record ReportFlowLevelResponse(ReportFlowResponse flow, @Nullable Double start, @Nullable Double end,
		InterestShift shift) {

	ReportFlowLevelResponse {
		Objects.requireNonNull(flow, "flow");
		Objects.requireNonNull(shift, "shift");
	}

	static ReportFlowLevelResponse of(ReportSectionStats.FlowLevel level, ReportFlows.Named named) {
		return new ReportFlowLevelResponse(Objects.requireNonNull(named.flows().get(level.storyline()), "flow"),
				level.start(), level.end(), level.shift());
	}

}
