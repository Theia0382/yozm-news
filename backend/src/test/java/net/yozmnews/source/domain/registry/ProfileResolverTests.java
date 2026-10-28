package net.yozmnews.source.domain.registry;

import static net.yozmnews.source.domain.registry.RegistryFixtures.INVALID_TEXT;
import static net.yozmnews.source.domain.registry.RegistryFixtures.NOW;
import static net.yozmnews.source.domain.registry.RegistryFixtures.PARSER;
import static net.yozmnews.source.domain.registry.RegistryFixtures.REVIEWED_TEXT;
import static net.yozmnews.source.domain.registry.RegistryFixtures.preset;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.source.domain.profile.Preset;
import net.yozmnews.source.domain.profile.PresetCatalog;
import net.yozmnews.source.domain.profile.PresetKey;
import org.junit.jupiter.api.Test;

/** 소스의 프로필 — 프리셋은 카탈로그에서, 운영자 프로필은 원문을 다시 검증해서 얻는다. */
class ProfileResolverTests {

	private static final Preset ALPHA = preset("alpha", REVIEWED_TEXT);

	private final ProfileResolver resolver = new ProfileResolver(new PresetCatalog(List.of(ALPHA)), PARSER);

	@Test
	void resolvesAPresetFromTheCatalog() {
		assertThat(this.resolver.resolve(Source.ofPreset(new PresetKey("alpha"), NOW)))
			.isEqualTo(new ProfileResolution.Resolved(ALPHA.profile()));
	}

	@Test
	void reportsAPresetMissingFromTheCatalog() {
		assertThat(this.resolver.resolve(Source.ofPreset(new PresetKey("gone"), NOW)))
			.isEqualTo(new ProfileResolution.PresetMissing(new PresetKey("gone")));
	}

	@Test
	void parsesTheStoredOperatorProfile() {
		ProfileResolution resolution = this.resolver.resolve(Source.ofOperatorProfile(REVIEWED_TEXT, NOW));

		assertThat(resolution).isInstanceOfSatisfying(ProfileResolution.Resolved.class,
				resolved -> assertThat(resolved.profile().source().name()).isEqualTo("합성일보"));
	}

	@Test
	void reportsAStoredOperatorProfileThatNoLongerValidates() {
		ProfileResolution resolution = this.resolver.resolve(Source.ofOperatorProfile(INVALID_TEXT, NOW));

		assertThat(resolution).isInstanceOfSatisfying(ProfileResolution.Invalid.class,
				invalid -> assertThat(invalid.problems()).isNotEmpty());
	}

}
