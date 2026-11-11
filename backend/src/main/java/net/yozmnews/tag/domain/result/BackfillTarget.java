package net.yozmnews.tag.domain.result;

import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.TagJudgementId;
import net.yozmnews.tag.TagSetId;

/**
 * 채움이 다시 읽을 평가 하나 — 평가 ID · 기사 · 버전 · 판정 원본 ID · docs/specs/tag.md §4
 *
 * @param judgementId 평가가 가리키는 태그 판정 원본 — 다시 읽기는 {@code evaluation} 이 이 ID 로 한다
 */
public record BackfillTarget(long evaluationId, ArticleId articleId, TagSetId tagSetId, TagJudgementId judgementId) {

	public BackfillTarget {
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(tagSetId, "tagSetId");
		Objects.requireNonNull(judgementId, "judgementId");
	}

}
