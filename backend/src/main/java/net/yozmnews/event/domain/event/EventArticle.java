package net.yozmnews.event.domain.event;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.domain.assignment.DecidedBy;
import org.jspecify.annotations.Nullable;

/**
 * 배정 한 행 — 기사마다 한 사건 · docs/specs/event.md §4.
 *
 * @param publishedAt 기사의 발행 시각
 * @param score 붙인 후보의 점수, 새 사건이면 가장 높은 후보의 점수 — 후보가 없으면 null
 * @param criteriaId 판정에 쓴 기준(기준 이력)
 * @param assignedAt 배정을 정한 시각
 */
public record EventArticle(ArticleId articleId, long eventId, Instant publishedAt, @Nullable Double score,
		DecidedBy decidedBy, long criteriaId, Instant assignedAt) {

	public EventArticle {
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(publishedAt, "publishedAt");
		Objects.requireNonNull(decidedBy, "decidedBy");
		Objects.requireNonNull(assignedAt, "assignedAt");
	}

}
