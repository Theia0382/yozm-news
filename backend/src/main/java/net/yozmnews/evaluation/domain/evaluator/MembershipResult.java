package net.yozmnews.evaluation.domain.evaluator;

/**
 * 사건 소속 판정 한 번의 결과(성공 · 예상한 실패) — docs/specs/evaluation.md §4
 */
public sealed interface MembershipResult permits Membership, EvaluationFailure {

}
