package net.yozmnews.tag.domain.tagset;

import java.util.Objects;

/**
 * Jev 에 묻는 태그 하나의 정의 — 주제 중분류(전체 키) 또는 지역 — docs/specs/tag.md §4
 */
public record TagDef(TagKey key, String name, String description) {

	public TagDef {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(description, "description");
	}

}
