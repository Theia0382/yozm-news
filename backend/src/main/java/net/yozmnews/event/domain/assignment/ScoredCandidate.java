package net.yozmnews.event.domain.assignment;

/**
 * 산식이 점수를 매긴 후보 사건 하나.
 *
 * @param score 0 ~ 1 — {@link CandidateScoring}
 */
public record ScoredCandidate(long eventId, double score) {
}
