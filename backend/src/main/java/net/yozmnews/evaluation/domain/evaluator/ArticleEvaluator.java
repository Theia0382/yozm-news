package net.yozmnews.evaluation.domain.evaluator;

import net.yozmnews.evaluation.TagSetView;

/**
 * 공유 평가의 포트 — Jev 를 부르는 유일한 포트 · docs/specs/evaluation.md §4
 */
public interface ArticleEvaluator {

	/** 기사 하나를 넘긴 체계의 질문으로 평가한다 — docs/specs/evaluation.md §4 */
	EvaluationResult evaluate(EvaluationInput input, TagSetView tagSet);

}
