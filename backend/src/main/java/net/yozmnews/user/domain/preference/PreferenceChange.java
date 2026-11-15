package net.yozmnews.user.domain.preference;

import java.util.Objects;

import net.yozmnews.tag.TagKind;

/**
 * 태그 선호 하나의 더함 · 뺌 — 모드는 태그 종류가 정한다({@link PreferenceMode#of}) — docs/specs/user.md §5 · user-R45
 */
public record PreferenceChange(TagKind kind, String key, PreferenceAction action) {

	public PreferenceChange {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(action, "action");
	}

	/** 이 선호의 모드 */
	public PreferenceMode mode() {
		return PreferenceMode.of(this.kind);
	}

}
