package net.yozmnews.tag.domain.tagset;

import java.util.Objects;

/**
 * 중요도 수준 하나의 기준 — docs/specs/tag.md §4 · tag-R7
 */
public record ImportanceLevelDef(int level, String description) {

	public ImportanceLevelDef {
		Objects.requireNonNull(description, "description");
	}

}
