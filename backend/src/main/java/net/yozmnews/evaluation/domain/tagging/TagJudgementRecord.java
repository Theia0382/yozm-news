package net.yozmnews.evaluation.domain.tagging;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.domain.evaluator.Evaluation;

/**
 * 저장할 태그 판정 원본 — docs/specs/evaluation.md §4 · evaluation-R41.
 *
 * @param articleId 판정한 기사
 * @param rawResponse 원본 응답 — JSON 텍스트
 * @param inputTokens 판정 요청의 입력 토큰
 * @param judgedAt 판정한 시각
 */
public record TagJudgementRecord(ArticleId articleId, String evaluator, String modelVersion, String rawResponse,
		int inputTokens, Instant judgedAt) {

	public TagJudgementRecord {
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(evaluator, "evaluator");
		Objects.requireNonNull(modelVersion, "modelVersion");
		Objects.requireNonNull(rawResponse, "rawResponse");
		Objects.requireNonNull(judgedAt, "judgedAt");
	}

	/**
	 * 판정 결과를 저장할 모양으로 옮긴다. 결과는 {@code ResultCheck} 로 질문 묶음과 맞는지 본 것이어야 한다.
	 */
	public static TagJudgementRecord of(ArticleId articleId, Evaluation evaluation, Instant judgedAt) {
		return new TagJudgementRecord(articleId, evaluation.evaluator(), evaluation.modelVersion(),
				evaluation.rawResponse(), evaluation.inputTokens(), judgedAt);
	}

}
