package net.yozmnews.reader.adapter.in.web;

/**
 * 목록 기간의 스토리라인 값 — docs/specs/reader.md §5 reader-R2.
 *
 * @param articles 목록 기간 안 기사 수(닫은 사건 집합 — 하위의 사건 포함)
 * @param big 목록 기간 · 지금 기준으로 큰 스토리라인인가
 */
record StorylinePeriodResponse(int articles, boolean big) {
}
