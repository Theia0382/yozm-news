package net.yozmnews.event;

import java.time.Instant;
import java.util.Objects;

/**
 * 사건 하나 · docs/specs/event.md §3 · event-R20.
 *
 * @param articleCount 구성 기사 수 — 기간과 무관한 사건 전체
 * @param firstArticleAt 구성 기사의 가장 이른 발행 시각
 * @param lastArticleAt 구성 기사의 가장 늦은 발행 시각
 * @param createdAt 사건을 연 시각
 */
public record EventView(EventId id, EventStatus status, int articleCount, Instant firstArticleAt, Instant lastArticleAt,
		Instant createdAt) {

	public EventView {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(firstArticleAt, "firstArticleAt");
		Objects.requireNonNull(lastArticleAt, "lastArticleAt");
		Objects.requireNonNull(createdAt, "createdAt");
	}

}
