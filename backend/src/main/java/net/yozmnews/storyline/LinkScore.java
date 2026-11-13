package net.yozmnews.storyline;

/**
 * 소속을 정한 점수 — 점수 · 사건 중심 코사인 · 무게를 준 핵심 말 겹침 · docs/specs/storyline.md §3 · storyline-R8.
 */
public record LinkScore(double score, double cosine, double keyTermOverlap) {

}
