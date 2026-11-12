package net.yozmnews.admin.adapter.in.web;

import java.util.List;

/**
 * 큰 판정 기준 미리보기 — 같은 기간을 지금 기준과 제안 기준으로 판정한 두 목록 · docs/specs/admin.md §3 · storyline-R20. 아무것도 쓰지 않는다.
 * 들어오고 빠지는 스토리라인은 화면이 두 목록의 스토리라인 ID 로 가린다.
 */
record BigStorylinePreviewResponse(BigStorylineCriteriaResponse current, BigStorylineCriteriaResponse proposed,
		List<BigStorylineRowResponse> currentStorylines, List<BigStorylineRowResponse> proposedStorylines) {
}
