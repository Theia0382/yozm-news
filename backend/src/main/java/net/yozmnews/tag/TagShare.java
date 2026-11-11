package net.yozmnews.tag;

import java.util.Objects;

/**
 * 태그 하나에 든 평가 수 — 판정이 그 태그를 붙인 평가(형식은 대표 형식이 그것인 평가) · docs/specs/tag.md §3
 *
 * @param key 태그 키 — 주제 중분류는 전체 키
 */
public record TagShare(String key, String name, Share share) {

	public TagShare {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(share, "share");
	}

}
