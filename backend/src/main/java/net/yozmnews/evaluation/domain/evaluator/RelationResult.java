package net.yozmnews.evaluation.domain.evaluator;

/**
 * 관계 판정 한 번의 결과(성공 · 예상한 실패) — docs/specs/evaluation.md §4
 */
public sealed interface RelationResult permits Relation, EvaluationFailure {

}
