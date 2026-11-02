package net.yozmnews.evaluation;

import java.util.Objects;

/**
 * 태그 하나 — 태그 종류와 키(중분류는 전체 키) — docs/specs/evaluation.md §3
 */
public record TagRef(TagKind kind, String key) {

	public TagRef {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(key, "key");
	}

}
