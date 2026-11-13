package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.event.EventStatus;
import net.yozmnews.event.EventView;
import net.yozmnews.storyline.StorylineEvent;
import org.jspecify.annotations.Nullable;

/**
 * 스토리라인 타임라인의 사건 — 사건 값 · 직접 소속인가 · 대표 기사 · 소속 근거 · docs/specs/admin.md §5 admin-R7.
 *
 * @param direct 상세의 스토리라인에 직접 들었으면 true, 하위에서 물려받았으면 false
 * @param memberships 그 사건을 직접 가진 스토리라인(자기 · 하위)마다 소속 근거 — 스토리라인 ID 순
 */
record StorylineEventResponse(long id, EventStatus status, int articleCount, Instant firstArticleAt,
		Instant lastArticleAt, boolean direct, @Nullable Long representativeArticleId,
		@Nullable ArticleSummaryResponse representative, List<StorylineMembershipResponse> memberships) {

	static StorylineEventResponse of(StorylineEvent event, AdminRepresentatives.Representative representative,
			List<StorylineMembershipResponse> memberships) {
		EventView view = event.event();
		return new StorylineEventResponse(view.id().value(), view.status(), view.articleCount(), view.firstArticleAt(),
				view.lastArticleAt(), event.direct(), representative.articleId(), representative.response(),
				memberships);
	}

}
