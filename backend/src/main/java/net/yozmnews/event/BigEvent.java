package net.yozmnews.event;

import java.util.List;
import java.util.Objects;

import net.yozmnews.article.ArticleId;

/**
 * 기간 안의 큰 사건 하나 · docs/specs/event.md §3 · event-R15.
 *
 * @param articles 기간 안 기사 — 발행 시각 · 기사 ID 순
 * @param tally 기간 안 집계
 */
public record BigEvent(EventId id, List<ArticleId> articles, EventTally tally) {

	public BigEvent {
		Objects.requireNonNull(id, "id");
		articles = List.copyOf(articles);
		Objects.requireNonNull(tally, "tally");
	}

}
