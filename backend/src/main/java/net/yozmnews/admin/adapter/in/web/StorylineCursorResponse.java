package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;

import net.yozmnews.storyline.StorylineCursor;

/**
 * 스토리라인 목록의 다음 쪽 커서 — 앞 쪽 마지막 스토리라인의 (직접 소속의 마지막 기사 시각, 스토리라인 ID) · docs/specs/admin.md §3.
 */
record StorylineCursorResponse(Instant lastArticleAt, long id) {

	static StorylineCursorResponse of(StorylineCursor cursor) {
		return new StorylineCursorResponse(cursor.lastArticleAt(), cursor.id().value());
	}

}
