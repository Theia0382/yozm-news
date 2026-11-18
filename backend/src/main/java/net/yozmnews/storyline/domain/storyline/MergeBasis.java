package net.yozmnews.storyline.domain.storyline;

/**
 * 조각 합치기의 근거 — 그 합치기를 정한 관계 판정 원본과 그때 쓴 관계 기준 이력 · docs/specs/storyline.md §4.
 * <p>
 * 합친 쌍은 한쪽이 사라져 물은 기록이 남지 않는다 — 근거는 합쳐진 행이 갖는다. 정돈의 합치기(구성 사건이 같음 · 사건 하나만 남음)에는 근거가 없다.
 *
 * @param judgementId 관계 판정 원본 ID
 * @param criteriaId 관계 기준 이력 ID
 */
public record MergeBasis(long judgementId, long criteriaId) {
}
