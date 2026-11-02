package net.yozmnews.evaluation.domain.threshold;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import net.yozmnews.evaluation.ThresholdWarning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 두 기준 변경 판정과 경고 — 값 리터럴로 판정과 경고 집합을 본다. "지금"은 태그 기준 / 저장 기준 순이다.
 * <p>
 * 지키는 규칙: evaluation-R10 ~ evaluation-R12
 */
class ThresholdRuleTests {

	@ParameterizedTest
	@CsvSource({ "-0.001, 0.1", "1.001, 0.1", "0.1234, 0.1", "0.5, -0.001", "0.5, 1.001", "0.5, 0.1234" })
	void refusesValuesOutOfRange(String tag, String store) {
		assertThat(decide("0.5", "0.1", "0.1", tag, store)).isInstanceOf(ThresholdDecision.OutOfRange.class);
	}

	@Test
	void doesNothingForTheSameValues() {
		assertThat(decide("0.5", "0.1", "0.1", "0.50", "0.1000")).isInstanceOf(ThresholdDecision.Unchanged.class);
	}

	@Test
	void acceptsTheEndsOfTheRange() {
		assertThat(warnings("0.5", "0.1", "0.1", "1", "0")).containsExactlyInAnyOrder(
				ThresholdWarning.STORE_THRESHOLD_LOWERED);
		assertThat(change("0.5", "0.1", "0.1", "1", "0").next())
			.isEqualTo(new ThresholdValues(new BigDecimal("1.000"), new BigDecimal("0.000")));
	}

	@Test
	void warnsWhenTheStoreThresholdIsLowered() {
		assertThat(warnings("0.5", "0.1", "0.1", "0.5", "0.05"))
			.containsExactlyInAnyOrder(ThresholdWarning.STORE_THRESHOLD_LOWERED);
	}

	@Test
	void warnsWhenTheTagThresholdIsLoweredBelowThePastStoreThreshold() {
		assertThat(warnings("0.5", "0.1", "0.3", "0.2", "0.1"))
			.containsExactlyInAnyOrder(ThresholdWarning.TAG_BELOW_PAST_STORE);
	}

	@Test
	void warnsWhenTheChangeBringsTheTagThresholdBelowTheStoreThreshold() {
		assertThat(warnings("0.5", "0.1", "0.1", "0.5", "0.6"))
			.containsExactlyInAnyOrder(ThresholdWarning.TAG_BELOW_STORE);
	}

	@Test
	void combinesWarnings() {
		assertThat(warnings("0.5", "0.1", "0.1", "0.05", "0.1")).containsExactlyInAnyOrder(
				ThresholdWarning.TAG_BELOW_PAST_STORE, ThresholdWarning.TAG_BELOW_STORE);
	}

	@Test
	void doesNotWarnWhenRaisingBothThresholds() {
		assertThat(warnings("0.5", "0.1", "0.1", "0.6", "0.2")).isEmpty();
	}

	@Test
	void doesNotWarnWhenTheTagThresholdIsLoweredToThePastStoreThreshold() {
		assertThat(warnings("0.5", "0.1", "0.3", "0.3", "0.1")).isEmpty();
	}

	@Test
	void doesNotWarnAgainWhenOnlyAnotherValueChangesInAnAlreadyMismatchedState() {
		assertThat(warnings("0.5", "0.1", "0.3", "0.6", "0.1")).isEmpty();
		assertThat(warnings("0.05", "0.1", "0.3", "0.07", "0.1")).isEmpty();
	}

	@Test
	void warnsOnlyAboutTheLoweredStoreThresholdWhenTheTagThresholdWasAlreadyBelowIt() {
		assertThat(warnings("0.05", "0.1", "0.3", "0.05", "0.08"))
			.containsExactlyInAnyOrder(ThresholdWarning.STORE_THRESHOLD_LOWERED);
	}

	private static ThresholdDecision decide(String currentTag, String currentStore, String pastMaxStore, String tag,
			String store) {
		return ThresholdRule.decide(new ThresholdValues(new BigDecimal(currentTag), new BigDecimal(currentStore)),
				new BigDecimal(pastMaxStore), new BigDecimal(tag), new BigDecimal(store));
	}

	private static ThresholdDecision.Change change(String currentTag, String currentStore, String pastMaxStore,
			String tag, String store) {
		ThresholdDecision decision = decide(currentTag, currentStore, pastMaxStore, tag, store);
		assertThat(decision).isInstanceOf(ThresholdDecision.Change.class);
		return (ThresholdDecision.Change) decision;
	}

	private static Set<ThresholdWarning> warnings(String currentTag, String currentStore, String pastMaxStore,
			String tag, String store) {
		return change(currentTag, currentStore, pastMaxStore, tag, store).warnings();
	}

}
