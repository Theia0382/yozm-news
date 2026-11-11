package net.yozmnews.tag.domain.tagset;

import java.util.List;
import java.util.Objects;

/**
 * 주제 대분류 하나의 정의와 그 중분류 — 대분류는 Jev 에 묻지 않는다 — docs/specs/tag.md §4 · tag-R7
 */
public record TopicCategoryDef(TagKey key, String name, String description, List<TagDef> subtopics) {

	public TopicCategoryDef {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(description, "description");
		subtopics = List.copyOf(subtopics);
	}

}
