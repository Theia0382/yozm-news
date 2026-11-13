package net.yozmnews.admin.adapter.in.web;

import java.util.List;

import net.yozmnews.event.EventPeriod;
import org.jspecify.annotations.Nullable;

/**
 * 기간의 스토리라인 한 쪽과 다음 커서, 그 기간의 소속 없는 사건, 지금 잇기 · 관계 기준, 날짜 범위의 상한 · docs/specs/admin.md §3 ·
 * admin-R7. 상한은 {@link EventPeriod#MAX_DAYS} 에서 읽는다.
 *
 * @param storylines 직접 소속의 마지막 기사 시각 · 스토리라인 ID 내림차순
 * @param next 다음 쪽 커서 — 더 없으면 null
 */
record StorylineListResponse(List<StorylineRowResponse> storylines, @Nullable StorylineCursorResponse next,
		UnlinkedEventsResponse unlinked, LinkingCriteriaResponse linkingCriteria,
		RelationCriteriaResponse relationCriteria, int maxDays) {

	static StorylineListResponse of(List<StorylineRowResponse> storylines, @Nullable StorylineCursorResponse next,
			UnlinkedEventsResponse unlinked, LinkingCriteriaResponse linkingCriteria,
			RelationCriteriaResponse relationCriteria) {
		return new StorylineListResponse(storylines, next, unlinked, linkingCriteria, relationCriteria,
				EventPeriod.MAX_DAYS);
	}

}
