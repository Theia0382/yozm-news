package net.yozmnews.event.domain.assignment;

import org.jspecify.annotations.Nullable;

/**
 * 판정에 쓰는 후보 사건 하나 — 저장 기준 이상인 후보이고, 배정과 함께 남아 재판정의 재료가 된다.
 *
 * @param score 산식의 점수 — {@link CandidateScoring}
 * @param probability 사건 소속 판정의 확률 — 묻지 않았으면 null
 */
public record StoredCandidate(long eventId, double score, @Nullable Double probability) {
}
