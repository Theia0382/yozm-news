package net.yozmnews.storyline.domain.storyline;

import org.jspecify.annotations.Nullable;

/**
 * 사건 하나의 직접 소속 하나와 그 소속 점수 — 꼭대기 조회의 재료 · docs/specs/storyline.md §4 · storyline-R14.
 *
 * @param eventId 사건
 * @param storylineId 사건이 직접 든 합쳐지지 않은 스토리라인
 * @param score 소속 점수 — 합치기로 옮긴 소속(MERGE)이면 null
 */
public record DirectMembership(long eventId, long storylineId, @Nullable Double score) {

}
