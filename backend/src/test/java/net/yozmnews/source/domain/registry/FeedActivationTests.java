package net.yozmnews.source.domain.registry;

import static net.yozmnews.source.domain.registry.RegistryFixtures.NOW;
import static net.yozmnews.source.domain.registry.RegistryFixtures.UNREVIEWED_TEXT;
import static net.yozmnews.source.domain.registry.RegistryFixtures.profile;
import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.source.domain.profile.PresetKey;
import org.junit.jupiter.api.Test;

/** 피드 켜기 — 소스의 프로필에 그 피드가 있어야 한다. 이용조건 검토일은 소스를 켤 때 본다. */
class FeedActivationTests {

	@Test
	void enablesAFeedOfTheProfileEvenWithoutTheTermsReviewDate() {
		Feed feed = feed("one");

		Activation activation = feed.enable(new ProfileResolution.Resolved(profile(UNREVIEWED_TEXT)));

		assertThat(activation).isEqualTo(new Activation.Activated());
		assertThat(feed.enabled()).isTrue();
	}

	@Test
	void refusesAFeedThatIsNotInTheProfile() {
		Feed feed = feed("gone");

		Activation activation = feed.enable(new ProfileResolution.Resolved(profile(UNREVIEWED_TEXT)));

		assertThat(activation).isEqualTo(new Activation.Refused(ActivationRefusal.FEED_NOT_IN_PROFILE));
		assertThat(feed.enabled()).isFalse();
	}

	@Test
	void refusesWhenTheSourceProfileIsUnavailable() {
		Feed feed = feed("one");

		Activation activation = feed.enable(new ProfileResolution.PresetMissing(new PresetKey("gone")));

		assertThat(activation).isEqualTo(new Activation.Refused(ActivationRefusal.PROFILE_UNAVAILABLE));
		assertThat(feed.enabled()).isFalse();
	}

	private static Feed feed(String key) {
		return new Feed(1L, 2L, key, false, null, 0L, NOW);
	}

}
