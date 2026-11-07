package net.yozmnews.event;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import org.jspecify.annotations.Nullable;

/**
 * 사건의 기사 하나 — 배정 행 · docs/specs/event.md §3 · event-R12 · event-R20.
 *
 * @param publishedAt 기사의 발행 시각
 * @param score 붙인 후보의 점수, 새 사건이면 가장 높은 후보의 점수 — 후보가 없었으면 null
 * @param decidedBy 배정을 정한 것
 * @param assignedAt 배정(또는 재판정으로 다시 적은) 시각
 */
public record EventArticleView(ArticleId articleId, Instant publishedAt, @Nullable Double score, DecidedBy decidedBy,
		Instant assignedAt) {

	public EventArticleView {
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(publishedAt, "publishedAt");
		Objects.requireNonNull(decidedBy, "decidedBy");
		Objects.requireNonNull(assignedAt, "assignedAt");
	}

}
