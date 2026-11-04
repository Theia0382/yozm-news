package net.yozmnews.evaluation;

import java.util.Objects;

/**
 * 저장된 주제 중분류 · 지역 태그 하나와 지금 기준으로 붙었는지 · docs/specs/evaluation.md §3.
 */
public record StoredTagView(String key, String name, double probability, boolean attached) {

	public StoredTagView {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
	}

}
