package net.yozmnews.reader.adapter.in.web;

import java.util.List;

import net.yozmnews.report.ReportSectionStats;

/**
 * 관심의 변화 — 주제 줄 · 사용자 스토리라인 줄. docs/specs/reader.md §5 reader-R16.
 */
record ReportInterestsResponse(List<ReportTopicLevelResponse> topics, List<ReportUserStorylineLevelResponse> userStorylines) {

	ReportInterestsResponse {
		topics = List.copyOf(topics);
		userStorylines = List.copyOf(userStorylines);
	}

	static ReportInterestsResponse of(ReportSectionStats.Interests interests, ReportNames.Named named) {
		return new ReportInterestsResponse(interests.topics().stream().map(ReportTopicLevelResponse::of).toList(),
				interests.userStorylines().stream().map(level -> ReportUserStorylineLevelResponse.of(level, named)).toList());
	}

}
