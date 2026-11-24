package net.yozmnews.reader.adapter.in.web;

import java.util.Objects;

import net.yozmnews.report.ReportSectionStats;
import net.yozmnews.report.ReportTrend;
import org.jspecify.annotations.Nullable;

/**
 * 추적한 흐름 하나의 보도량 — 이번 · 이은 직전 값 · 판정. docs/specs/reader.md §5 reader-R16.
 *
 * @param previous 이은 직전 값 — 없으면 null
 * @param trackedInPeriod 기간 안에 추적을 더했는가
 */
record ReportFlowCountResponse(ReportFlowResponse flow, long articles, @Nullable Long previous, ReportTrend trend,
		boolean trackedInPeriod) {

	ReportFlowCountResponse {
		Objects.requireNonNull(flow, "flow");
		Objects.requireNonNull(trend, "trend");
	}

	static ReportFlowCountResponse of(ReportSectionStats.FlowCount count, ReportFlows.Named named) {
		return new ReportFlowCountResponse(Objects.requireNonNull(named.flows().get(count.storyline()), "flow"),
				count.articles(), count.previous(), count.trend(), count.trackedInPeriod());
	}

}
