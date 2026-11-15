package net.yozmnews.user.domain.preference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagSetView;
import org.junit.jupiter.api.Test;

/**
 * 따라가기 규칙 — 이름 변경 · 분할 · 병합(모드별) · 계보 없이 지움 · 새 기본 제외 형식 · 대분류에서 옮겨 간 중분류 · 정규화. 체계 둘과 계보 표는
 * 손으로 적는다(이름은 "이름 " + 키).
 * <p>
 * 지키는 규칙: user-R47
 */
class PreferenceFollowupTests {

	@Test
	void aRenamedKeyIsRewrittenWithoutAnAdjustment() {
		TagSetView from = TagSets.of(1, topics("world", "politics", "trade"), List.of("us"), formats());
		TagSetView to = TagSets.of(2, topics("world", "affairs", "trade"), List.of("us"), formats());
		Mappings mapping = Mappings.identity(from, to).topic("world.politics", "world.affairs");

		FollowResult result = PreferenceFollowup.follow(
				new PreferenceSet(Set.of("world.politics"), Set.of(), Set.of("brief")), from, to, mapping.build());

		assertThat(result.next())
			.isEqualTo(new PreferenceSet(Set.of("world.affairs"), Set.of(), Set.of("brief")));
		assertThat(result.adjustments()).isEmpty();
	}

	@Test
	void aMergedExclusionContinuesOnlyWhenEverySourceWasExcluded() {
		TagSetView from = TagSets.of(1, topics("world", "trade"), List.of("us", "canada", "japan"), formats());
		TagSetView to = TagSets.of(2, topics("world", "trade"), List.of("north-america", "japan"), formats());
		TagKeyMapping mapping = Mappings.identity(from, to).region("us", "north-america")
			.region("canada", "north-america")
			.build();

		assertThat(PreferenceFollowup
			.follow(new PreferenceSet(Set.of(), Set.of("us", "canada"), Set.of()), from, to, mapping)
			.next()).isEqualTo(new PreferenceSet(Set.of(), Set.of("north-america"), Set.of()));
		FollowResult partial = PreferenceFollowup.follow(new PreferenceSet(Set.of(), Set.of("us", "japan"), Set.of()),
				from, to, mapping);
		assertThat(partial.next()).isEqualTo(new PreferenceSet(Set.of(), Set.of("japan"), Set.of()));
		assertThat(partial.adjustments()).containsExactly(new Adjustment(TagKind.REGION, "us", "이름 us",
				PreferenceMode.EXCLUDE, AdjustmentKind.RELEASED_PARTIAL_MERGE));
	}

	@Test
	void aMergeIntoOneOfItsSourcesIsNotOneToOne() {
		TagSetView from = TagSets.of(1, topics("world", "trade"), List.of("us", "canada"), formats());
		TagSetView to = TagSets.of(2, topics("world", "trade"), List.of("us"), formats());
		TagKeyMapping mapping = Mappings.identity(from, to).region("canada", "us").build();

		FollowResult result = PreferenceFollowup.follow(new PreferenceSet(Set.of(), Set.of("us"), Set.of()), from, to,
				mapping);

		assertThat(result.next()).isEqualTo(PreferenceSet.empty());
		assertThat(result.adjustments()).containsExactly(new Adjustment(TagKind.REGION, "us", "이름 us",
				PreferenceMode.EXCLUDE, AdjustmentKind.RELEASED_PARTIAL_MERGE));
	}

	@Test
	void aSplitExclusionGoesToEveryNewKeyAndOverlappingTargetsAreJudgedOneByOne() {
		TagSetView from = TagSets.of(1, topics("world", "trade"), List.of("americas", "asia"), formats());
		TagSetView to = TagSets.of(2, topics("world", "trade"), List.of("north", "south", "asia"), formats());
		TagKeyMapping split = Mappings.identity(from, to).region("americas", "north", "south").build();

		assertThat(PreferenceFollowup.follow(new PreferenceSet(Set.of(), Set.of("americas"), Set.of()), from, to, split)
			.next()).isEqualTo(new PreferenceSet(Set.of(), Set.of("north", "south"), Set.of()));

		// americas → {north, south}, asia → south: americas 만 제외했으면 north 는 제외, south 는 아니다
		TagKeyMapping overlap = Mappings.identity(from, to).region("americas", "north", "south")
			.region("asia", "south")
			.build();
		FollowResult result = PreferenceFollowup.follow(new PreferenceSet(Set.of(), Set.of("americas"), Set.of()), from,
				to, overlap);
		assertThat(result.next()).isEqualTo(new PreferenceSet(Set.of(), Set.of("north"), Set.of()));
		assertThat(result.adjustments()).containsExactly(new Adjustment(TagKind.REGION, "americas", "이름 americas",
				PreferenceMode.EXCLUDE, AdjustmentKind.RELEASED_PARTIAL_MERGE));
	}

	@Test
	void aNewFormatExcludedByDefaultIsAddedButAChangedDefaultOfAnOldFormatIsNot() {
		Map<String, Boolean> before = formats();
		Map<String, Boolean> after = new LinkedHashMap<>(formats());
		after.put("opinion", true);
		after.put("listicle", true);
		TagSetView from = TagSets.of(1, topics("world", "trade"), List.of("us"), before);
		TagSetView to = TagSets.of(2, topics("world", "trade"), List.of("us"), after);

		FollowResult result = PreferenceFollowup.follow(new PreferenceSet(Set.of(), Set.of(), Set.of()), from, to,
				Mappings.identity(from, to).build());

		assertThat(result.next()).isEqualTo(new PreferenceSet(Set.of(), Set.of(), Set.of("listicle")));
		assertThat(result.adjustments()).containsExactly(new Adjustment(TagKind.FORMAT, "listicle", "이름 listicle",
				PreferenceMode.EXCLUDE, AdjustmentKind.ADDED_DEFAULT_EXCLUDED));
	}

	@Test
	void aSubtopicMovedOutOfASubscribedCategoryIsAddedAsASubscription() {
		TagSetView from = TagSets.of(1, twoCategories(List.of("politics", "trade"), List.of("markets")), List.of("us"),
				formats());
		TagSetView to = TagSets.of(2, twoCategories(List.of("politics"), List.of("markets", "trade")), List.of("us"),
				formats());
		TagKeyMapping mapping = Mappings.identity(from, to).topic("world.trade", "economy.trade").build();

		FollowResult result = PreferenceFollowup.follow(new PreferenceSet(Set.of("world"), Set.of(), Set.of()), from, to,
				mapping);

		assertThat(result.next()).isEqualTo(new PreferenceSet(Set.of("world", "economy.trade"), Set.of(), Set.of()));
		assertThat(result.adjustments()).containsExactly(new Adjustment(TagKind.TOPIC, "economy.trade",
				"이름 economy.trade", PreferenceMode.SUBSCRIBE, AdjustmentKind.ADDED_MOVED_SUBTOPIC));
	}

	@Test
	void aSubtopicRemovedWithoutLineageIsReleased() {
		TagSetView from = TagSets.of(1, topics("world", "politics", "trade"), List.of("us"), formats());
		TagSetView to = TagSets.of(2, topics("world", "politics"), List.of("us"), formats());

		FollowResult result = PreferenceFollowup.follow(
				new PreferenceSet(Set.of("world.politics", "world.trade"), Set.of(), Set.of()), from, to,
				Mappings.identity(from, to).build());

		assertThat(result.next()).isEqualTo(new PreferenceSet(Set.of("world.politics"), Set.of(), Set.of()));
		assertThat(result.adjustments()).containsExactly(new Adjustment(TagKind.TOPIC, "world.trade", "이름 world.trade",
				PreferenceMode.SUBSCRIBE, AdjustmentKind.RELEASED_REMOVED));
	}

	@Test
	void aSubtopicRowMovedUnderASubscribedCategoryIsDropped() {
		TagSetView from = TagSets.of(1, twoCategories(List.of("politics", "trade"), List.of("markets")), List.of("us"),
				formats());
		TagSetView to = TagSets.of(2, twoCategories(List.of("politics"), List.of("markets", "trade")), List.of("us"),
				formats());
		TagKeyMapping mapping = Mappings.identity(from, to).topic("world.trade", "economy.trade").build();

		FollowResult result = PreferenceFollowup.follow(
				new PreferenceSet(Set.of("world.trade", "economy"), Set.of(), Set.of()), from, to, mapping);

		assertThat(result.next()).isEqualTo(new PreferenceSet(Set.of("economy"), Set.of(), Set.of()));
		assertThat(result.adjustments()).isEmpty();
	}

	@Test
	void aSubscriptionToOneSourceOfAMergeContinues() {
		TagSetView from = TagSets.of(1, topics("world", "politics", "diplomacy"), List.of("us"), formats());
		TagSetView to = TagSets.of(2, topics("world", "affairs"), List.of("us"), formats());
		TagKeyMapping mapping = Mappings.identity(from, to).topic("world.politics", "world.affairs")
			.topic("world.diplomacy", "world.affairs")
			.build();

		FollowResult result = PreferenceFollowup.follow(new PreferenceSet(Set.of("world.politics"), Set.of(), Set.of()),
				from, to, mapping);

		assertThat(result.next()).isEqualTo(new PreferenceSet(Set.of("world.affairs"), Set.of(), Set.of()));
		assertThat(result.adjustments()).isEmpty();
	}

	@Test
	void aCategoryRemovedWithoutLineageIsReleasedButItsContinuedSubtopicIsAdded() {
		TagSetView from = TagSets.of(1, twoCategories(List.of("politics", "trade"), List.of("markets")), List.of("us"),
				formats());
		Map<String, List<String>> onlyEconomy = new LinkedHashMap<>();
		onlyEconomy.put("economy", List.of("markets", "trade"));
		TagSetView to = TagSets.of(2, onlyEconomy, List.of("us"), formats());
		TagKeyMapping mapping = Mappings.identity(from, to).topic("world.trade", "economy.trade").build();

		FollowResult result = PreferenceFollowup.follow(new PreferenceSet(Set.of("world"), Set.of(), Set.of()), from, to,
				mapping);

		assertThat(result.next()).isEqualTo(new PreferenceSet(Set.of("economy.trade"), Set.of(), Set.of()));
		assertThat(result.adjustments()).containsExactly(
				new Adjustment(TagKind.TOPIC, "world", "이름 world", PreferenceMode.SUBSCRIBE,
						AdjustmentKind.RELEASED_REMOVED),
				new Adjustment(TagKind.TOPIC, "economy.trade", "이름 economy.trade", PreferenceMode.SUBSCRIBE,
						AdjustmentKind.ADDED_MOVED_SUBTOPIC));
	}

	@Test
	void anExclusionRemovedWithoutLineageIsReleased() {
		TagSetView from = TagSets.of(1, topics("world", "trade"), List.of("us", "china"), formats());
		TagSetView to = TagSets.of(2, topics("world", "trade"), List.of("us"), formats());

		FollowResult result = PreferenceFollowup.follow(new PreferenceSet(Set.of(), Set.of("us", "china"), Set.of()),
				from, to, Mappings.identity(from, to).build());

		assertThat(result.next()).isEqualTo(new PreferenceSet(Set.of(), Set.of("us"), Set.of()));
		assertThat(result.adjustments()).containsExactly(new Adjustment(TagKind.REGION, "china", "이름 china",
				PreferenceMode.EXCLUDE, AdjustmentKind.RELEASED_REMOVED));
	}

	@Test
	void aMappingForOtherVersionsIsRefused() {
		TagSetView from = TagSets.of(1, topics("world", "trade"), List.of("us"), formats());
		TagSetView to = TagSets.of(2, topics("world", "trade"), List.of("us"), formats());
		TagSetView other = TagSets.of(3, topics("world", "trade"), List.of("us"), formats());

		assertThatIllegalArgumentException().isThrownBy(
				() -> PreferenceFollowup.follow(PreferenceSet.empty(), from, to, Mappings.identity(from, other).build()));
	}

	private static Map<String, List<String>> topics(String category, String... subtopics) {
		Map<String, List<String>> topics = new LinkedHashMap<>();
		topics.put(category, List.of(subtopics));
		return topics;
	}

	private static Map<String, List<String>> twoCategories(List<String> world, List<String> economy) {
		Map<String, List<String>> topics = new LinkedHashMap<>();
		topics.put("world", world);
		topics.put("economy", economy);
		return topics;
	}

	private static Map<String, Boolean> formats() {
		Map<String, Boolean> formats = new LinkedHashMap<>();
		formats.put("straight", false);
		formats.put("opinion", false);
		formats.put("brief", true);
		return formats;
	}

	/**
	 * 손으로 적는 계보 표 — {@code from} 의 키 가운데 {@code to} 에 같은 키가 있으면 자기 하나, 없으면 빈 집합(계보 없이 지움)으로 시작하고
	 * 계보를 덧쓴다.
	 */
	private static final class Mappings {

		private final TagSetView from;

		private final TagSetView to;

		private final Map<TagKind, Map<String, Set<String>>> keys = new HashMap<>();

		private Mappings(TagSetView from, TagSetView to) {
			this.from = from;
			this.to = to;
		}

		static Mappings identity(TagSetView from, TagSetView to) {
			Mappings mappings = new Mappings(from, to);
			for (TagKind kind : TagKind.values()) {
				Set<String> toKeys = keysOf(to, kind);
				Map<String, Set<String>> byKey = new HashMap<>();
				keysOf(from, kind).forEach(key -> byKey.put(key, toKeys.contains(key) ? Set.of(key) : Set.of()));
				mappings.keys.put(kind, byKey);
			}
			return mappings;
		}

		Mappings topic(String fromKey, String... toKeys) {
			this.keys.get(TagKind.TOPIC).put(fromKey, Set.of(toKeys));
			return this;
		}

		Mappings region(String fromKey, String... toKeys) {
			this.keys.get(TagKind.REGION).put(fromKey, Set.of(toKeys));
			return this;
		}

		TagKeyMapping build() {
			return new TagKeyMapping(this.from.id(), this.to.id(), this.keys);
		}

		private static Set<String> keysOf(TagSetView version, TagKind kind) {
			return switch (kind) {
				case TOPIC -> {
					Set<String> keys = new java.util.HashSet<>();
					version.topics().forEach(category -> {
						keys.add(category.key());
						category.subtopics().forEach(subtopic -> keys.add(subtopic.key()));
					});
					yield keys;
				}
				case REGION -> Set.copyOf(version.regions().stream().map(region -> region.key()).toList());
				case FORMAT -> Set.copyOf(version.format().options().stream().map(option -> option.key()).toList());
			};
		}

	}

}
