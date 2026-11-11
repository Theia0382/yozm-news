package net.yozmnews.tag.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 표집 — 해시는 공개된 SplitMix64 참조 출력(씨앗 1234567 의 첫 다섯 출력)으로 고정하고, 고른 순서는 그 값의 부호 없는 크기 순을 손으로 늘어놓은
 * 리터럴로 본다.
 * <p>
 * 참조 출력: 6457827717110365317 · 3203168211198807973 · 9817491932198370423 · 4593380528125082431 · 16408922859458223821 —
 * 크기 순은 둘째 · 넷째 · 첫째 · 셋째 · 다섯째, 곧 평가 ID 1 · 3 · 0 · 2 · 4 다.
 * <p>
 * 지키는 규칙: tag-R34
 */
class EvaluationSamplerTests {

	private static final long SEED = 1_234_567L;

	@Test
	void theHashOfIdNIsTheNPlusFirstSplitMix64OutputFromTheSeed() {
		assertThat(Long.toUnsignedString(EvaluationSampler.hash(SEED, 0))).isEqualTo("6457827717110365317");
		assertThat(Long.toUnsignedString(EvaluationSampler.hash(SEED, 1))).isEqualTo("3203168211198807973");
		assertThat(Long.toUnsignedString(EvaluationSampler.hash(SEED, 2))).isEqualTo("9817491932198370423");
		assertThat(Long.toUnsignedString(EvaluationSampler.hash(SEED, 3))).isEqualTo("4593380528125082431");
		assertThat(Long.toUnsignedString(EvaluationSampler.hash(SEED, 4))).isEqualTo("16408922859458223821");
	}

	@Test
	void theChosenAreTheSmallestHashesInUnsignedOrder() {
		// 셋째 · 다섯째 출력은 부호 있는 수로는 음수다 — 부호 없이 견주면 가장 큰 둘이다(부호 있게 견주면 2 · 4 가 앞에 온다)
		assertThat(chosen(5, 0, 1, 2, 3, 4)).containsExactly(1L, 3L, 0L, 2L, 4L);
		assertThat(chosen(3, 0, 1, 2, 3, 4)).containsExactly(1L, 3L, 0L);
	}

	@Test
	void theChoiceDoesNotDependOnTheOrderOfAdding() {
		assertThat(chosen(3, 4, 3, 2, 1, 0)).containsExactly(1L, 3L, 0L);
	}

	@Test
	void anEvaluationLeavingThePopulationChangesAtMostItselfAndTheBoundary() {
		// 뽑히지 않은 ID 가 빠지면 그대로다
		assertThat(chosen(3, 0, 1, 2, 3)).containsExactly(1L, 3L, 0L);
		// 대조: 뽑힌 ID 3 이 빠지면 다음 해시의 ID 2 하나만 들어오고 나머지 순서는 그대로다
		assertThat(chosen(3, 0, 1, 2, 4)).containsExactly(1L, 0L, 2L);
	}

	@Test
	void aSampleLargerThanThePopulationTakesAllAndCountsThePopulation() {
		EvaluationSampler sampler = new EvaluationSampler(SEED, 200);
		for (long id = 0; id < 5; id++) {
			sampler.add(id);
		}

		assertThat(sampler.chosen()).containsExactly(1L, 3L, 0L, 2L, 4L);
		assertThat(sampler.population()).isEqualTo(5);
	}

	@Test
	void anotherSeedGivesAnotherHash() {
		assertThat(EvaluationSampler.hash(SEED + 1, 0)).isNotEqualTo(EvaluationSampler.hash(SEED, 0));
	}

	@Test
	void theSizeIsAtLeastOne() {
		assertThatThrownBy(() -> new EvaluationSampler(SEED, 0)).isInstanceOf(IllegalArgumentException.class);
	}

	private static List<Long> chosen(int size, long... ids) {
		EvaluationSampler sampler = new EvaluationSampler(SEED, size);
		for (long id : ids) {
			sampler.add(id);
		}
		return sampler.chosen();
	}

}
