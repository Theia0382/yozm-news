package net.yozmnews.evaluation;

import java.util.Objects;

import net.yozmnews.article.ArticleId;

/**
 * 태그 판정 요청 — 판정할 기사와 질문 묶음 · docs/specs/evaluation.md §3 · evaluation-R40.
 */
public record TaggingQuery(ArticleId articleId, TagQuestions questions) {

	public TaggingQuery {
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(questions, "questions");
	}

}
