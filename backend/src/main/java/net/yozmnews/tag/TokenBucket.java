package net.yozmnews.tag;

/**
 * 입력 토큰의 칸 하나 — {@code [lower, upper)} · docs/specs/tag.md §3
 *
 * @param count 그 칸에 든 평가 수
 */
public record TokenBucket(int lower, int upper, long count) {

}
