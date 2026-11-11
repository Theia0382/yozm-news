package net.yozmnews.tag;

import java.util.Objects;

/**
 * 이름과 확률이 있는 태그 하나 — 평가 버전의 키 · 이름 · docs/specs/tag.md §3.
 */
public record NamedTag(String key, String name, double probability) {

	public NamedTag {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
	}

}
