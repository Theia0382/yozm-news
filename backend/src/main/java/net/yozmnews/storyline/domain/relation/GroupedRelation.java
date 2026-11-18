package net.yozmnews.storyline.domain.relation;

import java.util.Objects;

/**
 * 조각 쌍의 판정이 맺은 고리 하나 — docs/specs/storyline.md §4.
 *
 * @param relation 그 고리
 * @param by 그 고리를 맺은 쌍
 */
public record GroupedRelation(Relation relation, GroupedBy by) {

	public GroupedRelation {
		Objects.requireNonNull(relation, "relation");
		Objects.requireNonNull(by, "by");
	}

}
