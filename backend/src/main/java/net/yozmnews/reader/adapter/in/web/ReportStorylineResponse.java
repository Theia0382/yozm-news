package net.yozmnews.reader.adapter.in.web;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 변화 리포트가 가리키는 스토리라인 하나의 표시 — 지금 스토리라인 ID · 대표 기사 · 그 스토리라인의 정리된 사용자 스토리라인. docs/specs/reader.md §5
 * reader-R16.
 *
 * @param storylineId 지금 스토리라인 — 정리가 없을 때 공용 타임라인으로 잇는다
 * @param title 그 스토리라인의 대표 사건 대표 기사 — 찾지 못하면 null
 * @param userStorylines 그 스토리라인의 지금 꼭대기를 추적 대상으로 가진 정리된 사용자 스토리라인 — 사용자 스토리라인 ID 순, 없으면 빈 목록
 */
record ReportStorylineResponse(long storylineId, @Nullable NewsArticleResponse title,
		List<UserStorylineResponses.RefResponse> userStorylines) {

	ReportStorylineResponse {
		userStorylines = List.copyOf(userStorylines);
	}

}
