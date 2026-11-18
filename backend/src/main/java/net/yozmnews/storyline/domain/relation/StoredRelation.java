package net.yozmnews.storyline.domain.relation;

import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 읽은 관계 — docs/specs/storyline.md §4.
 *
 * @param probability 맺은 판정에서 가장 높았던 답의 확률
 * @param judgementId 그 판정 원본 ID — 원본은 먼저 지워졌을 수 있다
 * @param criteriaId 그 판정에 쓴 관계 기준 이력 ID
 * @param linkedAt 그 관계를 맺은(마지막으로 고친) 시각
 * @param groupedBy 그 고리를 맺은 조각 쌍 — 그 고리의 두 끝이 스스로 판정받은 보통 관계면 null
 */
public record StoredRelation(Relation relation, double probability, long judgementId, long criteriaId,
		Instant linkedAt, @Nullable GroupedBy groupedBy) {

	public StoredRelation {
		Objects.requireNonNull(relation, "relation");
		Objects.requireNonNull(linkedAt, "linkedAt");
	}

}
