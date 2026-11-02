package net.yozmnews.evaluation;

import java.util.Objects;

/**
 * 중요도 수준 하나의 기준 — docs/specs/evaluation.md §3
 */
public record ImportanceLevel(int level, String description) {

	public ImportanceLevel {
		Objects.requireNonNull(description, "description");
	}

}
