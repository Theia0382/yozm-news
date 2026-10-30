package net.yozmnews.source.domain.profile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 프리셋 카탈로그 — docs/specs/source.md §4 · source-R12.
 * <p>
 * 프리셋 키로 프리셋을 찾는다. 비어 있을 수 있다.
 */
public final class PresetCatalog {

	private final Map<PresetKey, Preset> presets = new LinkedHashMap<>();

	/**
	 * 카탈로그를 만든다.
	 * @throws IllegalArgumentException 같은 키의 프리셋이 둘 이상일 때
	 */
	public PresetCatalog(List<Preset> presets) {
		for (Preset preset : presets) {
			Objects.requireNonNull(preset, "preset");
			if (this.presets.putIfAbsent(preset.key(), preset) != null) {
				throw new IllegalArgumentException("같은 키의 프리셋이 둘 이상이다: " + preset.key());
			}
		}
	}

	public Optional<Preset> find(PresetKey key) {
		return Optional.ofNullable(this.presets.get(key));
	}

	/** 프리셋을 받은 순서로 돌려준다 */
	public List<Preset> all() {
		return List.copyOf(this.presets.values());
	}

}
