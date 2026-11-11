package net.yozmnews.evaluation;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.article.ArticleId;

/**
 * 태그 판정 원본 하나의 평가기 · 모델 버전 · 입력 토큰 · 판정 시각 — 원본 응답은 담지 않는다 · docs/specs/evaluation.md §3 ·
 * evaluation-R42.
 *
 * @param articleId 판정한 기사
 * @param inputTokens 판정 요청의 입력 토큰
 * @param judgedAt 판정한 시각
 */
public record TagJudgementView(TagJudgementId id, ArticleId articleId, String evaluator, String modelVersion,
		int inputTokens, Instant judgedAt) {

	public TagJudgementView {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(evaluator, "evaluator");
		Objects.requireNonNull(modelVersion, "modelVersion");
		Objects.requireNonNull(judgedAt, "judgedAt");
	}

}
