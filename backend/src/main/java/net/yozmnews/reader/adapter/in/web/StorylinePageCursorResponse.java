package net.yozmnews.reader.adapter.in.web;

import java.time.Instant;

import net.yozmnews.storyline.StorylineCursor;

/**
 * 스토리라인 목록의 다음 쪽 커서 — docs/specs/reader.md §5 reader-R2. 다음 요청은 이 두 값과 응답의 {@code until} 을 넘긴다.
 */
record StorylinePageCursorResponse(Instant lastArticleAt, long id) {

	static StorylinePageCursorResponse of(StorylineCursor cursor) {
		return new StorylinePageCursorResponse(cursor.lastArticleAt(), cursor.id().value());
	}

}
