package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 후보 사건의 가중치 — 사건의 주제 중분류 가중치의 산술 평균. 기대값은 손으로 계산한 리터럴이다.
 */
class TopicInterestsTests {

	private final TopicInterests interests = new TopicInterests(Map.of("a", 1.2, "b", 0.8));

	@Test
	void anArticleOnALikedAndADislikedTopicAveragesBoth() {
		assertThat(this.interests.weightOf(Set.of("a", "b"))).isCloseTo(1.0, within(1e-12));
	}

	@Test
	void aTopicWithoutSignalsCountsAsNeutralInTheAverage() {
		// (1.2 + 1.0) ÷ 2
		assertThat(this.interests.weightOf(Set.of("a", "c"))).isCloseTo(1.1, within(1e-12));
	}

	@Test
	void anArticleWithoutTopicsIsNeutral() {
		assertThat(this.interests.weightOf(Set.of())).isEqualTo(1.0);
		assertThat(this.interests.weight("c")).isEqualTo(1.0);
	}

}
