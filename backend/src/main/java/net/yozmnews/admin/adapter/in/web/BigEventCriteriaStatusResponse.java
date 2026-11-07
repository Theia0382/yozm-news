package net.yozmnews.admin.adapter.in.web;

import java.util.List;

/**
 * 큰 사건 기준의 지금 값과 이력 — docs/specs/admin.md §3 · event-R21.
 *
 * @param changes 이력 — 최근 것부터
 */
record BigEventCriteriaStatusResponse(BigEventCriteriaResponse current, List<BigEventCriteriaHistoryResponse> changes) {
}
