package net.yozmnews.evaluation.domain.result;

import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.TagSetId;

/**
 * 채움이 다시 읽을 평가 하나 — 평가 ID · 기사 · 버전 · 평가기 이름과 원본 응답 · docs/specs/evaluation.md §4
 *
 * @param rawResponse 원본 응답 — 저장된 JSON 텍스트
 */
public record StoredResponse(long evaluationId, ArticleId articleId, TagSetId tagSetId, String evaluator,
		String rawResponse) {

	public StoredResponse {
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(tagSetId, "tagSetId");
		Objects.requireNonNull(evaluator, "evaluator");
		Objects.requireNonNull(rawResponse, "rawResponse");
	}

}
