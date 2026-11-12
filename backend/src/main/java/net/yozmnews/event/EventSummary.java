package net.yozmnews.event;

import java.util.Objects;

import net.yozmnews.article.ArticleId;
import org.jspecify.annotations.Nullable;

/**
 * 사건 목록의 한 줄 · docs/specs/event.md §3 · event-R20.
 *
 * @param tally 목록 기간 안 집계
 * @param representative 사건 기사 전체에서 고른 대표 기사(event-R14) — 배정이 없으면 null
 */
public record EventSummary(EventView event, EventTally tally, @Nullable ArticleId representative) {

	public EventSummary {
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(tally, "tally");
	}

}
