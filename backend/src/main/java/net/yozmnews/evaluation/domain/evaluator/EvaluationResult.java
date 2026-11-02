package net.yozmnews.evaluation.domain.evaluator;

/**
 * 평가 한 번의 결과(성공 · 예상한 실패) — docs/specs/evaluation.md §4
 */
public sealed interface EvaluationResult permits Evaluation, EvaluationFailure {

}
