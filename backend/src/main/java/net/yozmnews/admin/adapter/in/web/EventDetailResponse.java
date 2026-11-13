package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.event.EventStatus;
import net.yozmnews.event.EventView;
import org.jspecify.annotations.Nullable;

/**
 * 사건 하나의 상세 — docs/specs/admin.md §3 · admin-R7.
 *
 * @param requestedId 경로의 사건 ID — 합쳐진 사건이면 {@code id}(남는 사건)와 다르다
 * @param articleCount 사건 전체의 기사 수
 * @param representativeId 사건 기사 전체에서 고른 대표 기사 — 배정이 없으면 null
 * @param representative 대표 기사의 제목 · 출처 — 대표가 없거나 그새 지웠으면 null
 * @param articles 사건의 기사 — 발행 시각 · 기사 ID 순
 * @param mergedEvents 이 사건으로 합쳐진 사건 — 합친 시각 · 사건 ID 순
 * @param storylines 사건이 든 스토리라인 — 직접 든 것과 그 상위, 스토리라인 ID 순
 */
record EventDetailResponse(long requestedId, long id, EventStatus status, int articleCount, Instant firstArticleAt,
		Instant lastArticleAt, Instant createdAt, @Nullable Long representativeId,
		@Nullable ArticleSummaryResponse representative, List<EventArticleResponse> articles,
		List<MergedEventResponse> mergedEvents, List<EventStorylineResponse> storylines) {

	static EventDetailResponse of(long requestedId, EventView event, @Nullable Long representativeId,
			AdminArticles.@Nullable Summary representative, List<EventArticleResponse> articles,
			List<MergedEventResponse> mergedEvents, List<EventStorylineResponse> storylines) {
		return new EventDetailResponse(requestedId, event.id().value(), event.status(), event.articleCount(),
				event.firstArticleAt(), event.lastArticleAt(), event.createdAt(), representativeId,
				(representative == null) ? null : ArticleSummaryResponse.of(representative), articles, mergedEvents,
				storylines);
	}

}
