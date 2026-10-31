package net.yozmnews.source.domain.registry;

import static net.yozmnews.source.domain.registry.RegistryFixtures.UNREVIEWED_TEXT;
import static net.yozmnews.source.domain.registry.RegistryFixtures.profile;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import net.yozmnews.source.ActivationRefusal;
import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.profile.FeedKey;
import net.yozmnews.source.domain.profile.PollInterval;
import net.yozmnews.source.domain.profile.PollIntervalSettings;
import net.yozmnews.source.domain.profile.PresetKey;
import org.junit.jupiter.api.Test;

/**
 * 수집 대상 판정 — 합성 프로필로 본다. 프로필에 없는 피드를 주기 정책에 넘기지 않는지(정책은 그런 피드에서 예외를 던진다)도
 * 본다.
 * <p>
 * 지키는 규칙: source-R5 · source-R19
 */
class PollTargetTests {

	private static final CollectionProfile PROFILE = profile(UNREVIEWED_TEXT);

	@Test
	void aFeedOfTheProfileIsReadyWithItsUrlAndInterval() {
		PollTarget target = PollTarget.of(new ProfileResolution.Resolved(PROFILE), new FeedKey("two"));

		assertThat(target).isInstanceOfSatisfying(PollTarget.Ready.class, ready -> {
			assertThat(ready.descriptor().url()).isEqualTo(URI.create("https://synthetic.example/rss/two.xml"));
			assertThat(ready.interval(PollIntervalSettings.NONE)).isEqualTo(new PollInterval(Duration.ofMinutes(30)));
		});
	}

	@Test
	void aFeedMissingFromTheProfileIsUnavailableWithoutAskingThePolicy() {
		PollTarget target = PollTarget.of(new ProfileResolution.Resolved(PROFILE), new FeedKey("gone"));

		assertThat(target).isEqualTo(new PollTarget.Unavailable(ActivationRefusal.FEED_NOT_IN_PROFILE));
	}

	@Test
	void anUnavailableProfileMakesEveryFeedUnavailable() {
		assertThat(PollTarget.of(new ProfileResolution.PresetMissing(new PresetKey("gone")), new FeedKey("one")))
			.isEqualTo(new PollTarget.Unavailable(ActivationRefusal.PROFILE_UNAVAILABLE));
		assertThat(PollTarget.of(new ProfileResolution.Invalid(List.of()), new FeedKey("one")))
			.isEqualTo(new PollTarget.Unavailable(ActivationRefusal.PROFILE_UNAVAILABLE));
	}

}
