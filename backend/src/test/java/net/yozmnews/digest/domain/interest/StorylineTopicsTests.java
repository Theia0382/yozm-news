package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 스토리라인의 주제 몫 — 그 중분류가 붙은 사건 수 ÷ 사건 수. 주제가 없는 사건도 분모에 든다.
 */
class StorylineTopicsTests {

	@Test
	void eachSubtopicGetsTheShareOfRepresentativesCarryingIt() {
		// 사건 넷 — a 는 둘, b 는 둘, 하나는 주제 없음
		assertThat(StorylineTopics.shares(List.of(Set.of("a"), Set.of("a", "b"), Set.of("b"), Set.of())))
			.isEqualTo(Map.of("a", 0.5, "b", 0.5));
	}

	@Test
	void aSingleTopicStorylineHasTheWholeShare() {
		assertThat(StorylineTopics.shares(List.of(Set.of("a"), Set.of("a")))).isEqualTo(Map.of("a", 1.0));
	}

	@Test
	void withoutRepresentativesThereAreNoShares() {
		assertThat(StorylineTopics.shares(List.of())).isEmpty();
		assertThat(StorylineTopics.shares(List.of(Set.of()))).as("주제 없는 사건뿐").isEmpty();
	}

}
