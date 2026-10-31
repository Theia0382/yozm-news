package net.yozmnews.admin.adapter.in.web;

import java.util.List;

/**
 * 등록된 소스 목록과 수집 주기의 하한(분) — docs/specs/admin.md §3
 */
record SourceListResponse(int minimumPollIntervalMinutes, List<SourceResponse> sources) {

}
