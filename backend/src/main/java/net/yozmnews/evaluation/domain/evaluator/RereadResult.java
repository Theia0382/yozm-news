package net.yozmnews.evaluation.domain.evaluator;

/**
 * 저장된 원본 응답을 다시 읽은 결과(되찾은 평가 · 예상한 실패) — docs/specs/evaluation.md §4
 */
public sealed interface RereadResult permits Evaluation, RereadFailure {

}
