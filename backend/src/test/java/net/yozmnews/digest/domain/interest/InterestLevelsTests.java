package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * 관심 x 와 가중치 변환 · 관심 있음 — 기대값은 손으로 적은 리터럴이다(β 0.5 · θ 0.2).
 */
class InterestLevelsTests {

	private static final Offset<Double> EXACT = within(1e-12);

	@Test
	void weightsMoveTheInterestByTheStrength() {
		InterestLevels levels = new InterestLevels(Map.of("a", 0.4, "b", -1.0, "c", 0.0));

		TopicInterests weights = levels.weights();

		assertThat(weights.weight("a")).isCloseTo(1.2, EXACT);
		assertThat(weights.weight("b")).isCloseTo(0.5, EXACT);
		assertThat(weights.weight("c")).isCloseTo(1.0, EXACT);
		assertThat(weights.weight("d")).as("신호 없는 주제").isEqualTo(1.0);
	}

	@Test
	void presenceStartsAtTheThreshold() {
		InterestLevels levels = new InterestLevels(Map.of("on", 0.2, "below", 0.1999, "high", 0.9, "negative", -0.5));

		assertThat(levels.present()).containsExactlyInAnyOrder("on", "high");
	}

	@Test
	void aMissingTopicHasNoInterest() {
		assertThat(InterestLevels.NONE.level("a")).isEqualTo(0.0);
		assertThat(InterestLevels.NONE.present()).isEmpty();
		assertThat(InterestLevels.NONE.weights()).isEqualTo(TopicInterests.NEUTRAL_ALL);
	}

	@Test
	void anInterestOutsideMinusOneToOneIsRefused() {
		assertThatIllegalArgumentException().isThrownBy(() -> new InterestLevels(Map.of("a", 1.01)));
		assertThatIllegalArgumentException().isThrownBy(() -> new InterestLevels(Map.of("a", -1.01)));
		assertThatIllegalArgumentException().isThrownBy(() -> new InterestLevels(Map.of("a", Double.NaN)));
		assertThat(new InterestLevels(Map.of("a", 1.0, "b", -1.0)).levels()).as("양 끝은 받는다")
			.containsOnlyKeys("a", "b");
	}

}
