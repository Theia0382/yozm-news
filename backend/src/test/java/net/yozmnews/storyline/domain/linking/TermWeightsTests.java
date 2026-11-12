package net.yozmnews.storyline.domain.linking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 말의 무게 — 많은 사건에 있는 말일수록 작고, 수를 모르는 말은 어느 사건에도 없는 말로, 모든 사건에 있는 말은 0 으로 본다. 기대값은 손으로
 * 셈한 리터럴이다.
 * <p>
 * 지키는 규칙: storyline-R8
 */
class TermWeightsTests {

	@Test
	void aTermInFewerEventsWeighsMore() {
		TermWeights weights = TermWeights.of(99, Map.of("가", 9L, "나", 49L));

		// ln(100 ÷ 10) = 2.302585, ln(100 ÷ 50) = 0.693147
		assertThat(weights.weight("가")).isCloseTo(2.302585, within(1e-6));
		assertThat(weights.weight("나")).isCloseTo(0.693147, within(1e-6));
	}

	@Test
	void anUnknownTermCountsAsInNoEvent() {
		// ln(100 ÷ 1) = 4.605170
		assertThat(TermWeights.of(99, Map.of("가", 9L)).weight("다")).isCloseTo(4.605170, within(1e-6));
		assertThat(TermWeights.of(99, Map.of("다", 0L)).weight("다")).isCloseTo(4.605170, within(1e-6));
	}

	@Test
	void aTermInEveryEventWeighsNothing() {
		assertThat(TermWeights.of(9, Map.of("가", 9L)).weight("가")).isZero();
		// 두 수를 따로 세어 말을 가진 사건이 더 많게 읽혀도 음수가 되지 않는다
		assertThat(TermWeights.of(9, Map.of("가", 12L)).weight("가")).isZero();
		assertThat(TermWeights.of(0, Map.of()).weight("가")).isZero();
	}

	@Test
	void theCountsAreCopiedAndTheEventCountIsNotNegative() {
		Map<String, Long> counts = new HashMap<>(Map.of("가", 9L));
		TermWeights weights = TermWeights.of(99, counts);
		counts.put("가", 99L);

		assertThat(weights.weight("가")).isCloseTo(2.302585, within(1e-6));
		assertThatIllegalArgumentException().isThrownBy(() -> TermWeights.of(-1, Map.of()));
	}

}
