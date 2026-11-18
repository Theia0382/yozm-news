package net.yozmnews.storyline.domain.relation;

import java.time.Instant;
import java.util.Objects;

/**
 * 읽은 관계 — docs/specs/storyline.md §4.
 *
 * @param probability 맺은 판정에서 가장 높았던 답의 확률
 * @param judgementId 그 판정 원본 ID — 원본은 먼저 지워졌을 수 있다
 * @param criteriaId 그 판정에 쓴 관계 기준 이력 ID
 * @param linkedAt 그 관계를 맺은(마지막으로 고친) 시각
 */
public record StoredRelation(Relation relation, double probability, long judgementId, long criteriaId,
		Instant linkedAt) {

	public StoredRelation {
		Objects.requireNonNull(relation, "relation");
		Objects.requireNonNull(linkedAt, "linkedAt");
	}

}
