package net.yozmnews.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import net.yozmnews.article.ArticleId;

/**
 * 판정 원본 하나 · docs/specs/evaluation.md §3 · evaluation-R38 · evaluation-R39.
 *
 * @param articleId 판정할 기사
 * @param candidates 물은 후보 — 요청 순서
 * @param inputTokens 판정 요청의 입력 토큰
 * @param judgedAt 판정한 시각
 */
public record EventJudgementView(EventJudgementId id, ArticleId articleId, List<JudgedEvent> candidates,
		String evaluator, String modelVersion, String wordingVersion, int inputTokens, Instant judgedAt) {

	public EventJudgementView {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(articleId, "articleId");
		candidates = List.copyOf(candidates);
		Objects.requireNonNull(evaluator, "evaluator");
		Objects.requireNonNull(modelVersion, "modelVersion");
		Objects.requireNonNull(wordingVersion, "wordingVersion");
		Objects.requireNonNull(judgedAt, "judgedAt");
	}

}
