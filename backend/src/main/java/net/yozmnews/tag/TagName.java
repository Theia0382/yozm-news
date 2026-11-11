package net.yozmnews.tag;

import java.util.Objects;

/**
 * 키와 이름 — 대분류처럼 확률이 없는 태그 · docs/specs/tag.md §3.
 */
public record TagName(String key, String name) {

	public TagName {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
	}

}
