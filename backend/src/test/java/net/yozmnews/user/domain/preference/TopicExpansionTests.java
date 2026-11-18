package net.yozmnews.user.domain.preference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.tag.TagSetView;
import org.junit.jupiter.api.Test;

/**
 * 대분류 펼치기 — 대분류는 그 버전의 하위 중분류 전부, 중분류는 그 하나, 키 순. 없는 키는 거부
 */
class TopicExpansionTests {

	private static final TagSetView VERSION = TagSets.of(1,
			Map.of("politics", List.of("executive", "legislature"), "economy", List.of("markets")), List.of("china"),
			Map.of("brief", true));

	@Test
	void aCategoryCoversItsSubtopicsAndASubtopicCoversItself() {
		assertThat(TopicExpansion.expand(Set.of("politics", "economy.markets"), VERSION)).containsExactly(
				Map.entry("economy.markets", Set.of("economy.markets")),
				Map.entry("politics", Set.of("politics.executive", "politics.legislature")));
	}

	@Test
	void subtopicsOfFollowTheFileOrderAndAnUnknownCategoryHasNone() {
		assertThat(TopicExpansion.subtopicsOf(VERSION, "politics")).containsExactly("politics.executive",
				"politics.legislature");
		assertThat(TopicExpansion.subtopicsOf(VERSION, "sports")).isEmpty();
	}

	@Test
	void noSubscriptionsExpandToNothing() {
		assertThat(TopicExpansion.expand(Set.of(), VERSION)).isEmpty();
	}

	@Test
	void aKeyMissingFromTheVersionIsRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> TopicExpansion.expand(Set.of("sports"), VERSION));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> TopicExpansion.expand(Set.of("politics.unknown"), VERSION));
	}

}
