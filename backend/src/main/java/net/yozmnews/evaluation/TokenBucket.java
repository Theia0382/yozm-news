package net.yozmnews.evaluation;

/**
 * 입력 토큰의 칸 하나 — {@code [lower, upper)} · docs/specs/evaluation.md §3
 *
 * @param count 그 칸에 든 평가 수
 */
public record TokenBucket(int lower, int upper, long count) {

}
