package net.yozmnews.tag;

import java.util.Objects;

/**
 * 기사에 붙은 태그와 그 확률 — docs/specs/tag.md §3
 *
 * @param key 태그 키 — 주제는 중분류 전체 키
 */
public record TagProbability(String key, double probability) {

	public TagProbability {
		Objects.requireNonNull(key, "key");
	}

}
