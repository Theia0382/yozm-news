package net.yozmnews.source.domain.registry;

import static net.yozmnews.source.domain.registry.RegistryFixtures.NOW;
import static net.yozmnews.source.domain.registry.RegistryFixtures.REVIEWED_TEXT;
import static net.yozmnews.source.domain.registry.RegistryFixtures.UNREVIEWED_TEXT;
import static net.yozmnews.source.domain.registry.RegistryFixtures.profile;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.source.domain.profile.PresetKey;
import org.junit.jupiter.api.Test;

/**
 * 소스 켜기 — 합성 프로필로 aggregate 를 직접 본다.
 * <p>
 * 지키는 규칙: source-R4
 */
class SourceActivationTests {

	@Test
	void enablesASourceWhoseProfileHasTheTermsReviewDate() {
		Source source = Source.ofOperatorProfile(REVIEWED_TEXT, NOW);

		Activation activation = source.enable(new ProfileResolution.Resolved(profile(REVIEWED_TEXT)));

		assertThat(activation).isEqualTo(new Activation.Activated());
		assertThat(source.enabled()).isTrue();
	}

	@Test
	void refusesAProfileWithoutTheTermsReviewDate() {
		Source source = Source.ofOperatorProfile(UNREVIEWED_TEXT, NOW);

		Activation activation = source.enable(new ProfileResolution.Resolved(profile(UNREVIEWED_TEXT)));

		assertThat(activation).isEqualTo(new Activation.Refused(ActivationRefusal.TERMS_NOT_REVIEWED));
		assertThat(source.enabled()).isFalse();
	}

	@Test
	void refusesWhenThePresetIsGone() {
		Source source = Source.ofPreset(new PresetKey("gone"), NOW);

		Activation activation = source.enable(new ProfileResolution.PresetMissing(new PresetKey("gone")));

		assertThat(activation).isEqualTo(new Activation.Refused(ActivationRefusal.PROFILE_UNAVAILABLE));
		assertThat(source.enabled()).isFalse();
	}

	@Test
	void refusesWhenTheStoredProfileNoLongerValidates() {
		Source source = Source.ofOperatorProfile(REVIEWED_TEXT, NOW);

		Activation activation = source.enable(new ProfileResolution.Invalid(List.of()));

		assertThat(activation).isEqualTo(new Activation.Refused(ActivationRefusal.PROFILE_UNAVAILABLE));
		assertThat(source.enabled()).isFalse();
	}

}
