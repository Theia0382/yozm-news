package net.yozmnews.source.domain.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;
import java.time.Duration;

import net.yozmnews.source.domain.profile.text.ProfileTextReader;
import org.junit.jupiter.api.Test;

/**
 * 실제 수집 주기 — 운영자 피드 설정 → 운영자 소스 설정 → 프로필 피드 권장값 → 프로필 소스 권장값 → 15분. 이웃 두 단계에 늘 값이
 * 함께 있도록 위 단계부터 하나씩 비운다 — 순서가 바뀌면 드러난다.
 */
class PollIntervalPolicyTests {

	private static final String SOURCE = """
			source:
			  name: 블로터
			  homepage: https://www.bloter.net
			  language: ko
			""";

	private static final String FEEDS = """
			feeds:
			  - key: with-interval
			    url: https://a.example/rss
			    section: 전체
			    pollIntervalMinutes: 30
			  - key: without-interval
			    url: https://b.example/rss
			    section: 속보
			""";

	private final ProfileParser parser = new ProfileParser(new ProfileTextReader());

	private final CollectionProfile withSourceInterval = parse(SOURCE + FEEDS + "pollIntervalMinutes: 20\n");

	private final CollectionProfile withoutSourceInterval = parse(SOURCE + FEEDS);

	@Test
	void prefersTheOperatorFeedSetting() {
		assertThat(effective(this.withSourceInterval, "with-interval", minutes(45), minutes(60)))
			.isEqualTo(minutes(60));
	}

	@Test
	void fallsBackToTheOperatorSourceSetting() {
		assertThat(effective(this.withSourceInterval, "with-interval", minutes(45), null)).isEqualTo(minutes(45));
	}

	@Test
	void fallsBackToTheProfileFeedValue() {
		assertThat(effective(this.withSourceInterval, "with-interval", null, null)).isEqualTo(minutes(30));
	}

	@Test
	void fallsBackToTheProfileSourceValue() {
		assertThat(effective(this.withSourceInterval, "without-interval", null, null)).isEqualTo(minutes(20));
	}

	@Test
	void fallsBackToFifteenMinutes() {
		assertThat(effective(this.withoutSourceInterval, "without-interval", null, null)).isEqualTo(minutes(15));
	}

	@Test
	void appliesTheOperatorSettingAboveAProfileWithoutValues() {
		assertThat(effective(this.withoutSourceInterval, "without-interval", minutes(45), null)).isEqualTo(minutes(45));
	}

	@Test
	void rejectsAFeedOfAnotherProfile() {
		FeedSpec other = new FeedSpec(new FeedKey("with-interval"), URI.create("https://other.example/rss"), "전체", null);

		assertThatIllegalArgumentException().isThrownBy(
				() -> PollIntervalPolicy.effective(this.withSourceInterval, other, PollIntervalSettings.NONE));
	}

	@Test
	void buildsIntervalsOnlyFromTheMinimum() {
		assertThat(PollInterval.ofMinutes(14)).isEmpty();
		assertThat(PollInterval.ofMinutes(0)).isEmpty();
		assertThat(PollInterval.ofMinutes(-1)).isEmpty();
		assertThat(PollInterval.ofMinutes(15)).contains(new PollInterval(Duration.ofMinutes(15)));
		assertThatIllegalArgumentException().isThrownBy(() -> new PollInterval(Duration.ofMinutes(14)));
		assertThatIllegalArgumentException().isThrownBy(() -> new PollInterval(Duration.ofSeconds(899)));
		assertThat(new PollInterval(Duration.ofSeconds(900)).value()).isEqualTo(Duration.ofMinutes(15));
	}

	private PollInterval effective(CollectionProfile profile, String feedKey, PollInterval operatorSource,
			PollInterval operatorFeed) {
		FeedSpec feed = profile.feed(new FeedKey(feedKey)).orElseThrow();
		return PollIntervalPolicy.effective(profile, feed, new PollIntervalSettings(operatorSource, operatorFeed));
	}

	private static PollInterval minutes(long minutes) {
		return new PollInterval(Duration.ofMinutes(minutes));
	}

	private CollectionProfile parse(String text) {
		ProfileParse parse = this.parser.parseOperatorProfile(text);
		assertThat(parse).isInstanceOf(ProfileParse.Parsed.class);
		return ((ProfileParse.Parsed) parse).profile();
	}

}
