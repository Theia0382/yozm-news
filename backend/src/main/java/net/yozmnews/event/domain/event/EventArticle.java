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
 * @param opened 이 기사가 판정으로 이 배정의 사건을 열었다(처음 판정의 새 사건 · 재판정의 떼어 내기) — 붙이기 · 옮기기는 거짓, 병합으로
 * 사건이 바뀌어도 그대로다. 재판정이 "새 사건" 결과를 적용하는 갈래를 정한다
 * @param criteriaId 판정에 쓴 기준(기준 이력)
 * @param assignedAt 배정을 정한 시각
 */
public record EventArticle(ArticleId articleId, long eventId, Instant publishedAt, @Nullable Double score,
		DecidedBy decidedBy, boolean opened, long criteriaId, Instant assignedAt) {

	public EventArticle {
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(publishedAt, "publishedAt");
		Objects.requireNonNull(decidedBy, "decidedBy");
		Objects.requireNonNull(assignedAt, "assignedAt");
	}

}
