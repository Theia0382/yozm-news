package net.yozmnews.reader.adapter.in.web;

import java.util.List;

import net.yozmnews.report.ReportSectionStats;

/**
 * 관심의 변화 — 주제 줄 · 흐름 줄. docs/specs/reader.md §5 reader-R16.
 */
record ReportInterestsResponse(List<ReportTopicLevelResponse> topics, List<ReportFlowLevelResponse> flows) {

	ReportInterestsResponse {
		topics = List.copyOf(topics);
		flows = List.copyOf(flows);
	}

	static ReportInterestsResponse of(ReportSectionStats.Interests interests, ReportFlows.Named named) {
		return new ReportInterestsResponse(interests.topics().stream().map(ReportTopicLevelResponse::of).toList(),
				interests.userStorylines().stream().map(flow -> ReportFlowLevelResponse.of(flow, named)).toList());
	}

}
