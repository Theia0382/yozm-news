package net.yozmnews.evaluation;

import java.util.List;
import java.util.Objects;

/**
 * 주제 대분류와 그 중분류 — 대분류는 Jev 에 묻지 않아 질문이 없다 — docs/specs/evaluation.md §3 · tag-R7
 */
public record TopicCategory(String key, String name, String description, List<AskedTag> subtopics) {

	public TopicCategory {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(description, "description");
		subtopics = List.copyOf(subtopics);
	}

}
