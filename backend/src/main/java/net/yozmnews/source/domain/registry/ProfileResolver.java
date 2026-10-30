package net.yozmnews.source.domain.registry;

import java.util.Objects;
import java.util.Optional;

import net.yozmnews.source.domain.profile.PresetCatalog;
import net.yozmnews.source.domain.profile.PresetKey;
import net.yozmnews.source.domain.profile.ProfileParse;
import net.yozmnews.source.domain.profile.ProfileParser;

/**
 * 소스의 수집 프로필 해석기 — docs/specs/source.md §4 · source-R1 · source-R8
 */
public final class ProfileResolver {

	private final PresetCatalog catalog;

	private final ProfileParser parser;

	public ProfileResolver(PresetCatalog catalog, ProfileParser parser) {
		this.catalog = Objects.requireNonNull(catalog, "catalog");
		this.parser = Objects.requireNonNull(parser, "parser");
	}

	public ProfileResolution resolve(Source source) {
		Optional<PresetKey> presetKey = source.presetKey();
		if (presetKey.isPresent()) {
			PresetKey key = presetKey.get();
			return this.catalog.find(key)
				.<ProfileResolution>map(preset -> new ProfileResolution.Resolved(preset.profile()))
				.orElseGet(() -> new ProfileResolution.PresetMissing(key));
		}
		String text = source.operatorProfile().orElseThrow();
		return switch (this.parser.parseOperatorProfile(text)) {
			case ProfileParse.Parsed parsed -> new ProfileResolution.Resolved(parsed.profile());
			case ProfileParse.Invalid invalid -> new ProfileResolution.Invalid(invalid.problems());
		};
	}

}
