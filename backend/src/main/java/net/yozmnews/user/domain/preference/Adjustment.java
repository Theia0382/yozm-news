package net.yozmnews.user.domain.preference;

import java.util.Objects;

import net.yozmnews.tag.TagKind;

/**
 * 조정 항목 하나 — 해제는 옛 버전의 키 · 이름, 더함은 활성 버전의 키 · 이름 — docs/specs/user.md §5 · user-R47 · user-R48
 */
public record Adjustment(TagKind kind, String key, String name, PreferenceMode mode, AdjustmentKind change) {

	public Adjustment {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(change, "change");
	}

	/** 해제인가 — 이력에서 옛 키의 뺌이다 */
	public boolean released() {
		return switch (this.change) {
			case RELEASED_REMOVED, RELEASED_PARTIAL_MERGE -> true;
			case ADDED_MOVED_SUBTOPIC, ADDED_DEFAULT_EXCLUDED -> false;
		};
	}

}
