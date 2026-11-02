package net.yozmnews.source.domain.registry;

import static net.yozmnews.source.domain.registry.RegistryFixtures.UNREVIEWED_TEXT;
import static net.yozmnews.source.domain.registry.RegistryFixtures.profile;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.source.FeedRemovalRefusal;
import net.yozmnews.source.domain.profile.FeedKey;
import net.yozmnews.source.domain.profile.PresetKey;
import org.junit.jupiter.api.Test;

/**
 * 피드 숨기기 · 삭제의 대상 판정.
 * <p>
 * 지키는 규칙: source-R54
 */
class FeedRemovalRuleTests {

	private static final ProfileResolution RESOLVED = new ProfileResolution.Resolved(profile(UNREVIEWED_TEXT));

	@Test
	void aFeedMissingFromTheResolvedProfileIsATarget() {
		assertThat(FeedRemovalRule.refusal(RESOLVED, new FeedKey("gone"))).isEmpty();
	}

	@Test
	void aFeedOfTheProfileIsRefused() {
		assertThat(FeedRemovalRule.refusal(RESOLVED, new FeedKey("one"))).contains(FeedRemovalRefusal.IN_PROFILE);
	}

	@Test
	void aFeedOfASourceWithoutAProfileIsRefused() {
		assertThat(FeedRemovalRule.refusal(new ProfileResolution.PresetMissing(new PresetKey("gone")),
				new FeedKey("gone"))).contains(FeedRemovalRefusal.PROFILE_UNAVAILABLE);
		assertThat(FeedRemovalRule.refusal(new ProfileResolution.Invalid(List.of()), new FeedKey("gone")))
			.contains(FeedRemovalRefusal.PROFILE_UNAVAILABLE);
	}

}
