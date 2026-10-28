package net.yozmnews.source.domain.registry;

import java.util.Objects;
import java.util.Optional;

import net.yozmnews.source.domain.profile.PresetCatalog;
import net.yozmnews.source.domain.profile.PresetKey;
import net.yozmnews.source.domain.profile.ProfileParse;
import net.yozmnews.source.domain.profile.ProfileParser;

/**
 * 소스의 수집 프로필을 얻는다 — 프리셋은 카탈로그에서, 운영자 프로필은 저장된 원문을 검증해서. 가리킬 곳을 잃었는지는 저장하지 않고
 * 부를 때마다 판정한다 — 프리셋의 정본은 저장소 파일이고 카탈로그가 그 파일을 읽는다.
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
