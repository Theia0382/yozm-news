package net.yozmnews.reader.adapter.in.web;

import java.util.List;

/**
 * 관심사 히스토리의 정리된 사용자 스토리라인 하나 — docs/specs/reader.md §5 · reader-R8
 *
 * @param id 사용자 스토리라인 ID
 * @param title 사용자 스토리라인 전체의 제목
 * @param levels 기간마다 스토리라인 관심 — 기간 순, 신호가 없으면 null
 */
record InterestUserStorylineResponse(long id, String title, List<InterestLevelResponse> levels) {
}
