package net.yozmnews.evaluation.domain.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 평가 성공 — 값 하나하나의 검사. 어느 키 · 수준이 있어야 하는지는 검사하지 않는다.
 */
class EvaluationTests {

	@Test
	void acceptsTheEndsOfTheProbabilityRange() {
		Evaluation evaluation = evaluation(Map.of("politics.assembly", 0.0, "politics.executive", 1.0));

		assertThat(evaluation.topics()).containsEntry("politics.assembly", 0.0).containsEntry("politics.executive", 1.0);
	}

	@ParameterizedTest
	@ValueSource(doubles = { -0.000001, 1.000001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY })
	void refusesAProbabilityOutsideTheRange(double value) {
		assertThatIllegalArgumentException().isThrownBy(() -> evaluation(Map.of("politics.assembly", value)));
		assertThatIllegalArgumentException().isThrownBy(() -> new Evaluation(Map.of(), Map.of("domestic", value),
				Map.of(), Map.of(), "jev", "v1", 0, "{}"));
		assertThatIllegalArgumentException().isThrownBy(() -> new Evaluation(Map.of(), Map.of(),
				Map.of("straight", value), Map.of(), "jev", "v1", 0, "{}"));
		assertThatIllegalArgumentException().isThrownBy(() -> new Evaluation(Map.of(), Map.of(), Map.of(),
				Map.of(3, value), "jev", "v1", 0, "{}"));
	}

	@Test
	void refusesABlankKey() {
		assertThatIllegalArgumentException().isThrownBy(() -> evaluation(Map.of(" ", 0.5)));
	}

	@Test
	void doesNotCheckWhichImportanceLevelsArePresent() {
		// 수준의 범위는 체계가 정한다 — 이 레코드는 값만 본다
		Evaluation evaluation = new Evaluation(Map.of(), Map.of(), Map.of(), Map.of(7, 0.5), "jev", "v1", 0, "{}");

		assertThat(evaluation.importance()).containsEntry(7, 0.5);
	}

	@Test
	void refusesNegativeTokensAndAcceptsZero() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new Evaluation(Map.of(), Map.of(), Map.of(), Map.of(), "jev", "v1", -1, "{}"));
		assertThat(new Evaluation(Map.of(), Map.of(), Map.of(), Map.of(), "jev", "v1", 0, "{}").inputTokens())
			.isZero();
	}

	@Test
	void refusesABlankEvaluatorOrModelVersion() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new Evaluation(Map.of(), Map.of(), Map.of(), Map.of(), " ", "v1", 0, "{}"));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new Evaluation(Map.of(), Map.of(), Map.of(), Map.of(), "jev", "", 0, "{}"));
	}

	@Test
	void refusesAMissingRawResponseAndAcceptsAnyText() {
		assertThatNullPointerException()
			.isThrownBy(() -> new Evaluation(Map.of(), Map.of(), Map.of(), Map.of(), "jev", "v1", 0, null));
		assertThat(new Evaluation(Map.of(), Map.of(), Map.of(), Map.of(), "jev", "v1", 0, "").rawResponse()).isEmpty();
	}

	@Test
	void copiesTheProbabilities() {
		Map<String, Double> topics = new HashMap<>(Map.of("politics.assembly", 0.9));
		Evaluation evaluation = evaluation(topics);

		topics.put("politics.executive", 0.1);

		assertThat(evaluation.topics()).containsOnlyKeys("politics.assembly");
	}

	private static Evaluation evaluation(Map<String, Double> topics) {
		return new Evaluation(topics, Map.of(), Map.of(), Map.of(), "jev", "v1", 0, "{}");
	}

}
