package net.yozmnews.source.domain.profile;

import java.util.Objects;

/**
 * 프리셋 키 — 프리셋 파일 이름({@code {key}.yaml})이다. {@code sources.preset_key} 에 저장되므로 바꾸지 않는다.
 */
public record PresetKey(String value) {

	public PresetKey {
		Identifiers.requireSlug(Objects.requireNonNull(value, "value"), "프리셋 키");
	}

	@Override
	public String toString() {
		return this.value;
	}

}
