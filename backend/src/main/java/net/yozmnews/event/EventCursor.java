package net.yozmnews.event;

import java.time.Instant;
import java.util.Objects;

/**
 * 사건 목록의 다음 쪽 시작 — 앞 쪽 마지막 사건의 (마지막 기사 시각, 사건 ID) · docs/specs/event.md §3
 */
public record EventCursor(Instant lastArticleAt, EventId id) {

	public EventCursor {
		Objects.requireNonNull(lastArticleAt, "lastArticleAt");
		Objects.requireNonNull(id, "id");
	}

}
