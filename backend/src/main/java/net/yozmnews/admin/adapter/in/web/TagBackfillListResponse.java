package net.yozmnews.admin.adapter.in.web;

import java.util.List;

import net.yozmnews.tag.TagBackfillAdministration;
import net.yozmnews.tag.TagBackfillRunView;

/**
 * 빠진 태그 채움 목록(최근 것부터)과 채울 기간의 상한 — docs/specs/admin.md §3. 상한은 {@link TagBackfillAdministration#MAX_DAYS} 에서
 * 읽는다.
 */
record TagBackfillListResponse(List<TagBackfillRunResponse> runs, int maxDays) {

	static TagBackfillListResponse of(List<TagBackfillRunView> runs) {
		return new TagBackfillListResponse(runs.stream().map(TagBackfillRunResponse::of).toList(),
				TagBackfillAdministration.MAX_DAYS);
	}

}
