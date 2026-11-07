package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;

import net.yozmnews.event.EventCursor;

/**
 * 사건 목록의 다음 쪽 커서 — 질의 {@code afterLastArticleAt} · {@code afterId} 로 되돌려 준다 · docs/specs/admin.md §3.
 */
record EventCursorResponse(Instant lastArticleAt, long id) {

	static EventCursorResponse of(EventCursor cursor) {
		return new EventCursorResponse(cursor.lastArticleAt(), cursor.id().value());
	}

}
