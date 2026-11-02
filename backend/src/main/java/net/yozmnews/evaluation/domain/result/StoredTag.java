package net.yozmnews.evaluation.domain.result;

import java.util.Objects;

import net.yozmnews.evaluation.MultiTagKind;

/**
 * 저장된 주제 · 지역 태그 하나 — 저장 기준 이상인 확률 · docs/specs/evaluation.md §6
 *
 * @param key 태그 키 — 주제는 중분류 전체 키
 */
public record StoredTag(MultiTagKind kind, String key, double probability) {

	public StoredTag {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(key, "key");
	}

}
