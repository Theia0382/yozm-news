package net.yozmnews.tag.domain.review;

import java.util.Objects;
import java.util.Set;

import net.yozmnews.tag.MultiTagKind;

/**
 * 거르기의 필요조건 — 그 태그 종류의 이 키들 가운데 하나의 저장 행이 있다 · docs/specs/tag.md §4 · tag-R33.
 * <p>
 * 붙은 태그는 저장 행 가운데서 고르므로 이 조건을 지나지 않은 평가는 거르기에 맞지 않는다. 붙었는지는 판정이 정한다.
 */
public record StoredKeys(MultiTagKind kind, Set<String> keys) {

	/**
	 * @throws IllegalArgumentException 키가 없을 때
	 */
	public StoredKeys {
		Objects.requireNonNull(kind, "kind");
		keys = Set.copyOf(keys);
		if (keys.isEmpty()) {
			throw new IllegalArgumentException("필요조건의 키가 없다");
		}
	}

}
