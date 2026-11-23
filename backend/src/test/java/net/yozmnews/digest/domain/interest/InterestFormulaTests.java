package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.feedback.FeedbackValue;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * 관심도 산식 — 기대값은 산식을 시험 안에서 다시 계산하지 않고 손으로 계산해 적은 리터럴이다(상수: 반감기 30일 · β 0.5 · γ 0.1 · K 3 — 구독
 * 신호는 노출 분모 밖의 항). 노출 사건의 몫은 따로 적지 않으면 1 이다.
 */
class InterestFormulaTests {

	private static final Instant T = Instant.parse("2026-11-17T00:00:00Z");

	private static final Offset<Double> EXACT = within(1e-12);

	/** 구독 신호는 노출 분모 밖의 항 — 리터럴 상수 묶음(반감기 30일 · K 3 · γ 0.1 · β 0.5) */
	private static final InterestConstants SUBSCRIPTION_OUTSIDE = new InterestConstants(Duration.ofDays(30), 3.0, 0.1,
			0.5);

	@Test
	void withoutSignalsEveryTopicIsNeutral() {
		TopicInterests interests = weights(List.of(), List.of(), T);

		assertThat(interests).isEqualTo(TopicInterests.NEUTRAL_ALL);
		assertThat(interests.weight("a")).isEqualTo(1.0);
		assertThat(interests.weightOf(Set.of("a", "b"))).isEqualTo(1.0);
	}

	@Test
	void aSubscriptionWithoutExposureAddsTheSubscriptionValue() {
		// x = 0 ÷ (0 + 3) + 0.1 × 1 = 0.1 → g = 1 + 0.5 × 0.1
		TopicInterests interests = weights(List.of(), List.of(added(Map.of("a", 1.0), T)), T);

		assertThat(interests.weight("a")).isCloseTo(1.05, EXACT);
		assertThat(interests.weight("b")).as("신호 없는 주제").isEqualTo(1.0);
	}

	@Test
	void reactionsAreNormalizedByExposures() {
		// 노출 10 · 좋아요 3 · 싫어요 1 → x = 2 ÷ 13 → g = 1 + 1/13
		List<ExposedEvent<String>> exposed = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			exposed.add(exposed(Set.of("a"), T, FeedbackValue.LIKE));
		}
		exposed.add(exposed(Set.of("a"), T, FeedbackValue.DISLIKE));
		for (int i = 0; i < 6; i++) {
			exposed.add(exposed(Set.of("a"), T, FeedbackValue.NONE));
		}

		assertThat(weights(exposed, List.of(), T).weight("a")).isCloseTo(1.0769230769230769, EXACT);
	}

	@Test
	void dislikesLowerTheWeight() {
		// 노출 2 · 싫어요 2 → x = −2 ÷ 5 → g = 0.8
		TopicInterests interests = weights(List.of(exposed(Set.of("a"), T, FeedbackValue.DISLIKE),
				exposed(Set.of("a"), T, FeedbackValue.DISLIKE)), List.of(), T);

		assertThat(interests.weight("a")).isCloseTo(0.8, EXACT);
	}

	@Test
	void olderExposuresWeighLessByTheHalfLife() {
		// 30일 전 노출 · 좋아요 → 무게 0.5 → x = 0.5 ÷ 3.5 → g = 1 + 1/14
		Instant monthAgo = T.minus(Duration.ofDays(30));
		TopicInterests interests = weights(List.of(exposed(Set.of("a"), monthAgo, FeedbackValue.LIKE)), List.of(), T);

		assertThat(interests.weight("a")).isCloseTo(1.0714285714285714, EXACT);
		assertThat(InterestFormula.decay(T.minus(Duration.ofDays(60)), T, InterestRules.TOPIC.halfLife()))
			.isCloseTo(0.25, EXACT);
	}

	@Test
	void olderSubscriptionChangesWeighLessByTheHalfLife() {
		// 30일 전 주제 구독 더함 · 노출 0 → S = 0.5 → x = 0.1 × 0.5 = 0.05 → g = 1.025
		TopicInterests interests = weights(List.of(),
				List.of(added(Map.of("a", 1.0), T.minus(Duration.ofDays(30)))), T);

		assertThat(interests.weight("a")).isCloseTo(1.025, EXACT);
	}

	@Test
	void theReadRangeIsFourHalfLives() {
		assertThat(InterestRules.lookback()).isEqualTo(Duration.ofDays(120));
	}

	@Test
	void signalsAfterTheReferenceTimeWeighAsNow() {
		// 기준 시각 하루 뒤 노출 · 좋아요 → 무게 1 → x = 1 ÷ 4 → g = 1.125
		TopicInterests interests = weights(
				List.of(exposed(Set.of("a"), T.plus(Duration.ofDays(1)), FeedbackValue.LIKE)), List.of(), T);

		assertThat(interests.weight("a")).isCloseTo(1.125, EXACT);
	}

	@Test
	void theInterestIsClampedToOne() {
		// 노출 0 · 더함 11 → x = 0.1 × 11 = 1.1 → 1 로 자름 → g = 1.5
		SubscriptionSignal<String> add = added(Map.of("a", 1.0), T);
		TopicInterests interests = weights(List.of(), Collections.nCopies(11, add), T);

		assertThat(interests.weight("a")).isCloseTo(1.5, EXACT);
	}

	@Test
	void anEventOnSeveralTopicsCountsForEachOfThem() {
		// 노출 {a, b} 하나 · 좋아요 → a · b 모두 x = 1 ÷ 4 → g = 1.125
		TopicInterests interests = weights(List.of(exposed(Set.of("a", "b"), T, FeedbackValue.LIKE)), List.of(), T);

		assertThat(interests.weight("a")).isCloseTo(1.125, EXACT);
		assertThat(interests.weight("b")).isCloseTo(1.125, EXACT);
	}

	@Test
	void aShareScalesTheSubscriptionSignalAndARemovalSubtracts() {
		// 노출 1(없음) · 몫 0.5 의 뺌 → x = 0 ÷ 4 + 0.1 × (−0.5) = −0.05 → g = 0.975
		TopicInterests interests = weights(List.of(exposed(Set.of("a"), T, FeedbackValue.NONE)),
				List.of(new SubscriptionSignal<>(Map.of("a", 0.5), -1, T)), T);

		assertThat(interests.weight("a")).isCloseTo(0.975, EXACT);
	}

	@Test
	void levelsGiveTheInterestBeforeItBecomesAWeight() {
		// 노출 10 · 좋아요 5 → x = 5 ÷ 13 · g = 15.5 ÷ 13, 노출 2 · 싫어요 2 → x = −2 ÷ 5 · g = 0.8, 신호 없는 주제는 0
		List<ExposedEvent<String>> exposed = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			exposed.add(exposed(Set.of("a"), T, FeedbackValue.LIKE));
			exposed.add(exposed(Set.of("a"), T, FeedbackValue.NONE));
		}
		exposed.add(exposed(Set.of("b"), T, FeedbackValue.DISLIKE));
		exposed.add(exposed(Set.of("b"), T, FeedbackValue.DISLIKE));

		InterestLevels levels = levels(exposed, List.of(), T);

		assertThat(levels.level("a")).isCloseTo(0.38461538461538464, EXACT);
		assertThat(levels.level("b")).isCloseTo(-0.4, EXACT);
		assertThat(levels.level("c")).isEqualTo(0.0);
		assertThat(levels.levels()).containsOnlyKeys("a", "b");
		TopicInterests interests = weights(exposed, List.of(), T);
		assertThat(interests.weight("a")).as("가중치는 같은 관심의 변환").isCloseTo(1.1923076923076923, EXACT);
		assertThat(interests.weight("b")).isCloseTo(0.8, EXACT);
	}

	@Test
	void theShareOfAnEventInATwoEventItemWeighsBothItsExposureAndItsReaction() {
		// 사건 둘인 항목의 사건 하나(몫 1/2) · 좋아요 · 기준 시각에 노출(무게 1) → E = 0.5 · F = 0.5 → x = 0.5 ÷ 3.5 = 1/7
		InterestLevels levels = levels(List.of(exposed(Set.of("a"), T, 0.5, FeedbackValue.LIKE)), List.of(), T);

		assertThat(levels.level("a")).isCloseTo(0.14285714285714285, EXACT);
	}

	@Test
	void anEventAloneInItsItemWeighsOne() {
		// 대조 — 몫 1 이면 E = 1 · F = 1 → x = 1 ÷ 4
		InterestLevels levels = levels(List.of(exposed(Set.of("a"), T, 1.0, FeedbackValue.LIKE)), List.of(), T);

		assertThat(levels.level("a")).isCloseTo(0.25, EXACT);
	}

	@Test
	void aKeyMultipleScalesBothTheExposureAndTheReactionOfThatKey() {
		// 몫 배수 0.5 인 키 · 좋아요 · 몫 1 → E = 0.5 · F = 0.5 → x = 0.5 ÷ 3.5 = 1/7, 배수 1 인 키는 1 ÷ 4
		Map<String, Double> levels = InterestFormula.levels(
				List.of(new ExposedEvent<>(Map.of("half", 0.5, "whole", 1.0), T, 1.0, FeedbackValue.LIKE)), List.of(), T,
				InterestRules.TOPIC);

		// 대조 — 배수 1 인 키는 노출 · 반응이 줄지 않는다
		assertThat(levels.get("whole")).isCloseTo(0.25, EXACT);
		assertThat(levels.get("half")).isCloseTo(0.14285714285714285, EXACT);
	}

	@Test
	void theConstantsComeFromTheGivenBundle() {
		// 같은 재료(노출 1 · 좋아요)에 K 1 묶음 → x = 1 ÷ (1 + 1) = 0.5 — 주제 묶음(K 3)이면 0.25
		InterestConstants priorOne = new InterestConstants(Duration.ofDays(30), 1.0, 1.0, 0.5);
		List<ExposedEvent<String>> exposed = List.of(exposed(Set.of("a"), T, FeedbackValue.LIKE));

		assertThat(InterestFormula.levels(exposed, List.of(), T, priorOne).get("a")).isCloseTo(0.5, EXACT);
		assertThat(InterestFormula.levels(exposed, List.of(), T, InterestRules.TOPIC).get("a")).isCloseTo(0.25, EXACT);
	}

	@Test
	void theHalfLifeComesFromTheGivenBundle() {
		// 60일 전 노출 · 좋아요 — 주제 묶음(반감기 30일)이면 무게 0.25 → x = 0.25 ÷ 3.25 = 1/13,
		// 반감기 60일 묶음이면 무게 0.5 → x = 0.5 ÷ 3.5 = 1/7
		InterestConstants halfLifeSixty = new InterestConstants(Duration.ofDays(60), 3.0, 1.0, 0.5);
		List<ExposedEvent<String>> exposed = List
			.of(exposed(Set.of("a"), T.minus(Duration.ofDays(60)), FeedbackValue.LIKE));

		assertThat(InterestFormula.levels(exposed, List.of(), T, InterestRules.TOPIC).get("a"))
			.isCloseTo(0.07692307692307693, EXACT);
		assertThat(InterestFormula.levels(exposed, List.of(), T, halfLifeSixty).get("a"))
			.isCloseTo(0.14285714285714285, EXACT);
	}

	@Test
	void theSubscriptionSignalComesFromTheGivenBundle() {
		// 노출 0 · 주제 구독 더함 하나 — 주제 묶음(γ 0.1)이면 x = 0.1, γ 0.25 묶음이면 x = 0.25
		InterestConstants signalQuarter = new InterestConstants(Duration.ofDays(30), 3.0, 0.25, 0.5);
		List<SubscriptionSignal<String>> signals = List.of(added(Map.of("a", 1.0), T));

		assertThat(InterestFormula.levels(List.of(), signals, T, InterestRules.TOPIC).get("a")).isCloseTo(0.1, EXACT);
		assertThat(InterestFormula.levels(List.of(), signals, T, signalQuarter).get("a")).isCloseTo(0.25, EXACT);
	}

	@Test
	void aSubscriptionIsNotDilutedByExposures() {
		// 구독 1 · 노출 30 · 반응 0 → x = 0 ÷ 33 + 0.1 × 1 = 0.1 (노출이 늘어도 그대로)
		List<ExposedEvent<String>> exposed = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			exposed.add(exposed(Set.of("a"), T, FeedbackValue.NONE));
		}
		List<SubscriptionSignal<String>> signals = List.of(added(Map.of("a", 1.0), T));

		assertThat(InterestFormula.levels(exposed, signals, T, SUBSCRIPTION_OUTSIDE).get("a")).isCloseTo(0.1, EXACT);
		assertThat(InterestFormula.levels(List.of(), signals, T, SUBSCRIPTION_OUTSIDE).get("a"))
			.as("노출 0 이어도 같은 값")
			.isCloseTo(0.1, EXACT);
	}

	@Test
	void reactionsAndASubscriptionAreAddedOutsideTheDenominator() {
		// 노출 9 · 좋아요 3 · 구독 1 → x = 3 ÷ (9 + 3) + 0.1 = 0.35
		List<ExposedEvent<String>> exposed = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			exposed.add(exposed(Set.of("a"), T, FeedbackValue.LIKE));
		}
		for (int i = 0; i < 6; i++) {
			exposed.add(exposed(Set.of("a"), T, FeedbackValue.NONE));
		}

		assertThat(InterestFormula.levels(exposed, List.of(added(Map.of("a", 1.0), T)), T, SUBSCRIPTION_OUTSIDE).get("a"))
			.isCloseTo(0.35, EXACT);
	}

	@Test
	void theSumIsClampedToOne() {
		// γ 0.5 묶음 · 노출 9 · 좋아요 9 · 구독 1 → 9 ÷ 12 + 0.5 = 1.25 → 1
		InterestConstants half = new InterestConstants(Duration.ofDays(30), 3.0, 0.5, 0.5);
		List<ExposedEvent<String>> exposed = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			exposed.add(exposed(Set.of("a"), T, FeedbackValue.LIKE));
		}

		assertThat(InterestFormula.levels(exposed, List.of(added(Map.of("a", 1.0), T)), T, half).get("a"))
			.isEqualTo(1.0);
	}

	private static InterestLevels levels(List<ExposedEvent<String>> exposed, List<SubscriptionSignal<String>> signals,
			Instant reference) {
		return new InterestLevels(InterestFormula.levels(exposed, signals, reference, InterestRules.TOPIC));
	}

	private static TopicInterests weights(List<ExposedEvent<String>> exposed, List<SubscriptionSignal<String>> signals,
			Instant reference) {
		return levels(exposed, signals, reference).weights();
	}

	private static ExposedEvent<String> exposed(Set<String> subtopics, Instant at, FeedbackValue reaction) {
		return exposed(subtopics, at, 1.0, reaction);
	}

	private static ExposedEvent<String> exposed(Set<String> subtopics, Instant at, double share,
			FeedbackValue reaction) {
		Map<String, Double> keys = new HashMap<>();
		subtopics.forEach(subtopic -> keys.put(subtopic, 1.0));
		return new ExposedEvent<>(keys, at, share, reaction);
	}

	private static SubscriptionSignal<String> added(Map<String, Double> shares, Instant at) {
		return new SubscriptionSignal<>(shares, 1, at);
	}

}
