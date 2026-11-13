package net.yozmnews.evaluation.domain.evaluator;

/**
 * 저장된 관계 판정 원본 응답을 다시 읽은 결과 — 읽은 관계 판정이거나 예상한 이유로 읽지 못함 · docs/specs/evaluation.md §4 ·
 * evaluation-R50
 */
public sealed interface RelationRereadResult permits Relation, RereadFailure {

}
