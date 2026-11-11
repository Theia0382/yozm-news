package net.yozmnews.tag.domain.tagset;

import java.util.Objects;

/**
 * 형식 태그 하나의 정의 — Choice 선택지 — docs/specs/tag.md §4
 */
public record FormatDef(TagKey key, String name, String description, boolean excludedByDefault) {

	public FormatDef {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(description, "description");
	}

}
