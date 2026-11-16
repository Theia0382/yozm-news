package net.yozmnews.reader.adapter.in.web;

import java.time.Instant;

import net.yozmnews.storyline.StorylineState;
import net.yozmnews.storyline.StorylineView;
import org.jspecify.annotations.Nullable;

/**
 * 스토리라인 카드 — 대표 기사 · 상태 · 닫은 사건 집합의 사건 수 · 기사 수 · 첫 · 마지막 기사 시각 · 상위. docs/specs/reader.md §5 reader-R3.
 * 보도 강도 · 소스 수 · 중요도는 싣지 않는다.
 *
 * @param title 대표 사건의 대표 기사 — 대표가 없거나(대표 사건이 그새 합쳐졌거나 지워짐) 기사가 지워졌으면 null
 * @param parentId 이 스토리라인을 포함하는 상위 — 없으면 null
 * @param period 목록 기간의 값 — 목록 행에만 있고 타임라인 · 카드에서는 null
 */
record StorylineCardResponse(long id, @Nullable NewsArticleResponse title, StorylineState state, int eventCount,
		int articleCount, Instant firstArticleAt, Instant lastArticleAt, @Nullable Long parentId,
		@Nullable StorylinePeriodResponse period) {

	static StorylineCardResponse of(StorylineView view, @Nullable NewsArticleResponse title,
			@Nullable StorylinePeriodResponse period) {
		return new StorylineCardResponse(view.id().value(), title, view.state(), view.eventCount(), view.articleCount(),
				view.firstArticleAt(), view.lastArticleAt(), (view.parent() == null) ? null : view.parent().value(),
				period);
	}

}
