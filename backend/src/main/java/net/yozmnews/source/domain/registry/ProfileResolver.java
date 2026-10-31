package net.yozmnews.source.domain.registry;

import java.util.Objects;

import net.yozmnews.source.domain.profile.PresetCatalog;
import net.yozmnews.source.domain.profile.PresetKey;
import net.yozmnews.source.domain.profile.ProfileParse;
import net.yozmnews.source.domain.profile.ProfileParser;
import org.jspecify.annotations.Nullable;

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
		return resolve(source.presetKey().orElse(null), source.operatorProfile().orElse(null));
	}

	/** 목록의 소스 행에서 프로필을 얻는다 — 저장된 소스와 같은 판정이다 */
	public ProfileResolution resolve(SourceListingRepository.SourceRow row) {
		return resolve((row.presetKey() != null) ? new PresetKey(row.presetKey()) : null, row.profile());
	}

	private ProfileResolution resolve(@Nullable PresetKey presetKey, @Nullable String operatorProfile) {
		if (presetKey != null) {
			return this.catalog.find(presetKey)
				.<ProfileResolution>map(preset -> new ProfileResolution.Resolved(preset.profile()))
				.orElseGet(() -> new ProfileResolution.PresetMissing(presetKey));
		}
		return switch (this.parser.parseOperatorProfile(Objects.requireNonNull(operatorProfile, "operatorProfile"))) {
			case ProfileParse.Parsed parsed -> new ProfileResolution.Resolved(parsed.profile());
			case ProfileParse.Invalid invalid -> new ProfileResolution.Invalid(invalid.problems());
		};
	}

}
