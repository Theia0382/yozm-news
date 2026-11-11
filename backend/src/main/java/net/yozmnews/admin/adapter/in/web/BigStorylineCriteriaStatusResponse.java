package net.yozmnews.admin.adapter.in.web;

import java.util.List;

/**
 * 큰 판정 기준의 지금 값과 이력 — docs/specs/admin.md §3 · storyline-R20.
 *
 * @param changes 이력 — 최근 것부터
 */
record BigStorylineCriteriaStatusResponse(BigStorylineCriteriaResponse current, List<BigStorylineCriteriaHistoryResponse> changes) {
}
