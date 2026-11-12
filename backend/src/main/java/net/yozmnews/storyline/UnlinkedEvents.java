package net.yozmnews.storyline;

/**
 * 조회 기간의 소속 없는 사건 · docs/specs/storyline.md §3 · storyline-R23.
 *
 * @param events 조회 기간 안 기사가 있는데 어느 스토리라인에도 소속이 없는 사건 수
 * @param articles 그 사건들의 조회 기간 안 기사 수
 */
public record UnlinkedEvents(int events, int articles) {
}
