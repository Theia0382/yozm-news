package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.storyline.StorylineState;
import net.yozmnews.storyline.StorylineView;
import org.jspecify.annotations.Nullable;

/**
 * 스토리라인 하나 — 값 · 대표 기사 · 타임라인 · 관계 · 물은 쌍 · docs/specs/admin.md §3 · admin-R7.
 *
 * @param requestedId 경로의 ID — 합쳐진 스토리라인의 ID 면 남는 스토리라인({@code id})과 다르다
 * @param parentId 이 스토리라인을 포함하는 상위 — 없으면 null
 * @param events 닫은 사건 집합의 사건 — 첫 기사 시각 · 사건 ID 순
 * @param relations 이 스토리라인이 앞이거나 뒤인 관계 — 종류(포함 먼저) · 앞 ID · 뒤 ID 순
 * @param asked 이 스토리라인이 든 물은 쌍 — 상대 ID 순
 */
record StorylineDetailResponse(long requestedId, long id, StorylineState state, @Nullable Long parentId,
		int eventCount, int articleCount, Instant firstArticleAt, Instant lastArticleAt, Instant createdAt,
		@Nullable Long representativeArticleId, @Nullable ArticleSummaryResponse representative,
		List<StorylineEventResponse> events, List<StorylineRelationResponse> relations,
		List<StorylineAskedResponse> asked) {

	static StorylineDetailResponse of(long requestedId, StorylineView view,
			AdminRepresentatives.Representative representative, List<StorylineEventResponse> events,
			List<StorylineRelationResponse> relations, List<StorylineAskedResponse> asked) {
		return new StorylineDetailResponse(requestedId, view.id().value(), view.state(),
				(view.parent() == null) ? null : view.parent().value(), view.eventCount(), view.articleCount(),
				view.firstArticleAt(), view.lastArticleAt(), view.createdAt(), representative.articleId(),
				representative.response(), events, relations, asked);
	}

}
