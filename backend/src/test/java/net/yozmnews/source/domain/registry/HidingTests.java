package net.yozmnews.source.domain.registry;

import static net.yozmnews.source.domain.registry.RegistryFixtures.feed;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import net.yozmnews.source.domain.profile.PresetKey;
import org.junit.jupiter.api.Test;

/**
 * 소스 · 피드의 숨김 전이 — 숨김은 끄기를 포함하고, 다시 보이면 꺼진 채다.
 * <p>
 * 지키는 규칙: source-R53
 */
class HidingTests {

	private static final Instant FIRST = Instant.parse("2026-11-01T00:00:00Z");

	private static final Instant LATER = Instant.parse("2026-11-02T00:00:00Z");

	@Test
	void hidingASourceTurnsItOffAndUnhidingKeepsItOff() {
		Source source = Source.ofPreset(new PresetKey("multi"), FIRST);
		source.enable(new ProfileResolution.Resolved(RegistryFixtures.profile(RegistryFixtures.REVIEWED_TEXT)));
		assertThat(source.enabled()).isTrue();

		source.hide(FIRST);

		assertThat(source.hidden()).isTrue();
		assertThat(source.enabled()).isFalse();

		source.unhide();

		assertThat(source.hidden()).isFalse();
		assertThat(source.enabled()).isFalse();
	}

	@Test
	void hidingAFeedTurnsItOffAndUnhidingKeepsItOff() {
		Feed feed = feed("one", true);

		feed.hide(FIRST);

		assertThat(feed.hidden()).isTrue();
		assertThat(feed.enabled()).isFalse();

		feed.unhide();

		assertThat(feed.hidden()).isFalse();
		assertThat(feed.enabled()).isFalse();
	}

	@Test
	void hidingTwiceKeepsTheFirstTime() {
		Feed feed = feed("one", false);
		feed.hide(FIRST);
		feed.hide(LATER);

		assertThat(feed).extracting("hiddenAt").isEqualTo(FIRST);
	}

}
