package net.yozmnews.storyline.domain.relation;

import java.time.Instant;
import java.util.Objects;

/**
 * 읽은 관계 — docs/specs/storyline.md §4.
 *
 * @param linkedAt 그 관계를 맺은(마지막으로 고친) 시각
 */
public record StoredRelation(Relation relation, Instant linkedAt) {

	public StoredRelation {
		Objects.requireNonNull(relation, "relation");
		Objects.requireNonNull(linkedAt, "linkedAt");
	}

}
