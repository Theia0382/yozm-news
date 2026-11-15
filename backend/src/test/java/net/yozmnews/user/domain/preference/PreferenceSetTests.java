package net.yozmnews.user.domain.preference;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagRef;
import net.yozmnews.tag.TagSetView;
import org.junit.jupiter.api.Test;

/**
 * 태그 선호 집합 — 계정을 만들 때의 기본값, 모르는 키, 대분류 정규화, 차이, 종류마다의 모드.
 * <p>
 * 지키는 규칙: user-R43 · user-R45
 */
class PreferenceSetTests {

	private static final TagSetView VERSION = TagSets.of(1, topics(), List.of("domestic", "us"), formats());

	@Test
	void onboardingExcludesEveryFormatExcludedByDefaultAndNothingElse() {
		assertThat(PreferenceSet.onboarding(VERSION))
			.isEqualTo(new PreferenceSet(Set.of(), Set.of(), Set.of("press-release", "brief")));
	}

	@Test
	void onboardingOfAVersionWithoutDefaultsIsEmpty() {
		TagSetView none = TagSets.of(2, topics(), List.of("domestic"), Map.of("straight", false, "opinion", false));

		assertThat(PreferenceSet.onboarding(none)).isEqualTo(PreferenceSet.empty());
	}

	@Test
	void unknownKeysAreListedByKindThenKey() {
		PreferenceSet preferences = new PreferenceSet(Set.of("politics", "politics.executive", "sports", "economy.nope"),
				Set.of("us", "mars"), Set.of("brief", "listicle"));

		assertThat(preferences.unknownKeys(VERSION)).containsExactly(new TagRef(TagKind.TOPIC, "economy.nope"),
				new TagRef(TagKind.TOPIC, "sports"), new TagRef(TagKind.REGION, "mars"),
				new TagRef(TagKind.FORMAT, "listicle"));
		assertThat(new PreferenceSet(Set.of("politics", "economy.markets"), Set.of("us"), Set.of("brief"))
			.unknownKeys(VERSION)).isEmpty();
	}

	@Test
	void aSubscribedCategoryCoversItsSubtopics() {
		PreferenceSet preferences = new PreferenceSet(Set.of("politics", "politics.executive", "economy.markets"),
				Set.of("us"), Set.of("brief"));

		assertThat(preferences.normalized(VERSION))
			.isEqualTo(new PreferenceSet(Set.of("politics", "economy.markets"), Set.of("us"), Set.of("brief")));
	}

	@Test
	void subtopicsWithoutTheirCategoryStay() {
		PreferenceSet preferences = new PreferenceSet(Set.of("politics.executive", "economy.markets"), Set.of(),
				Set.of());

		assertThat(preferences.normalized(VERSION)).isEqualTo(preferences);
	}

	@Test
	void changesRemoveFirstThenAddEachByKindThenKey() {
		PreferenceSet before = new PreferenceSet(Set.of("politics", "economy"), Set.of("us"), Set.of("brief"));
		PreferenceSet after = new PreferenceSet(Set.of("politics", "economy.markets"), Set.of("domestic"),
				Set.of("brief", "press-release"));

		assertThat(before.changesTo(after)).containsExactly(
				new PreferenceChange(TagKind.TOPIC, "economy", PreferenceAction.REMOVE),
				new PreferenceChange(TagKind.REGION, "us", PreferenceAction.REMOVE),
				new PreferenceChange(TagKind.TOPIC, "economy.markets", PreferenceAction.ADD),
				new PreferenceChange(TagKind.REGION, "domestic", PreferenceAction.ADD),
				new PreferenceChange(TagKind.FORMAT, "press-release", PreferenceAction.ADD));
		assertThat(after.changesTo(after)).isEmpty();
	}

	@Test
	void topicsAreSubscriptionsAndRegionsAndFormatsAreExclusions() {
		assertThat(PreferenceMode.of(TagKind.TOPIC)).isEqualTo(PreferenceMode.SUBSCRIBE);
		assertThat(PreferenceMode.of(TagKind.REGION)).isEqualTo(PreferenceMode.EXCLUDE);
		assertThat(PreferenceMode.of(TagKind.FORMAT)).isEqualTo(PreferenceMode.EXCLUDE);
		assertThat(new PreferenceChange(TagKind.FORMAT, "brief", PreferenceAction.ADD).mode())
			.isEqualTo(PreferenceMode.EXCLUDE);
	}

	private static Map<String, List<String>> topics() {
		Map<String, List<String>> topics = new LinkedHashMap<>();
		topics.put("politics", List.of("executive", "assembly"));
		topics.put("economy", List.of("markets", "trade"));
		return topics;
	}

	private static Map<String, Boolean> formats() {
		Map<String, Boolean> formats = new LinkedHashMap<>();
		formats.put("straight", false);
		formats.put("press-release", true);
		formats.put("brief", true);
		return formats;
	}

}
