package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.feedback.FeedbackValue;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * 관심도 산식 — 기대값은 산식을 시험 안에서 다시 계산하지 않고 손으로 계산해 적은 리터럴이다(상수: 반감기 30일 · β 0.5 · γ 1 · K 3).
 */
class InterestFormulaTests {

	private static final Instant T = Instant.parse("2026-11-17T00:00:00Z");

	private static final Offset<Double> EXACT = within(1e-12);

	@Test
	void withoutSignalsEveryTopicIsNeutral() {
		TopicInterests interests = InterestFormula.interests(List.of(), List.of(), T);

		assertThat(interests).isEqualTo(TopicInterests.NEUTRAL_ALL);
		assertThat(interests.weight("a")).isEqualTo(1.0);
		assertThat(interests.weightOf(Set.of("a", "b"))).isEqualTo(1.0);
	}

	@Test
	void aSubscriptionWithoutExposureIsSmoothedByThePriorExposures() {
		// x = 1 ÷ (0 + 3) → g = 1 + 0.5/3
		TopicInterests interests = InterestFormula.interests(List.of(), List.of(added(Map.of("a", 1.0), T)), T);

		assertThat(interests.weight("a")).isCloseTo(1.1666666666666667, EXACT);
		assertThat(interests.weight("b")).as("신호 없는 주제").isEqualTo(1.0);
	}

	@Test
	void reactionsAreNormalizedByExposures() {
		// 노출 10 · 좋아요 3 · 싫어요 1 → x = 2 ÷ 13 → g = 1 + 1/13
		List<ExposedArticle> exposed = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			exposed.add(exposed(Set.of("a"), T, FeedbackValue.LIKE));
		}
		exposed.add(exposed(Set.of("a"), T, FeedbackValue.DISLIKE));
		for (int i = 0; i < 6; i++) {
			exposed.add(exposed(Set.of("a"), T, FeedbackValue.NONE));
		}

		assertThat(InterestFormula.interests(exposed, List.of(), T).weight("a")).isCloseTo(1.0769230769230769, EXACT);
	}

	@Test
	void dislikesLowerTheWeight() {
		// 노출 2 · 싫어요 2 → x = −2 ÷ 5 → g = 0.8
		TopicInterests interests = InterestFormula.interests(List.of(exposed(Set.of("a"), T, FeedbackValue.DISLIKE),
				exposed(Set.of("a"), T, FeedbackValue.DISLIKE)), List.of(), T);

		assertThat(interests.weight("a")).isCloseTo(0.8, EXACT);
	}

	@Test
	void olderExposuresWeighLessByTheHalfLife() {
		// 30일 전 노출 · 좋아요 → 무게 0.5 → x = 0.5 ÷ 3.5 → g = 1 + 1/14
		Instant monthAgo = T.minus(Duration.ofDays(30));
		TopicInterests interests = InterestFormula
			.interests(List.of(exposed(Set.of("a"), monthAgo, FeedbackValue.LIKE)), List.of(), T);

		assertThat(interests.weight("a")).isCloseTo(1.0714285714285714, EXACT);
		assertThat(InterestFormula.decay(T.minus(Duration.ofDays(60)), T)).isCloseTo(0.25, EXACT);
	}

	@Test
	void olderSubscriptionChangesWeighLessByTheHalfLife() {
		// 30일 전 주제 구독 더함 · 노출 0 → S = 0.5 → x = 0.5 ÷ 3 → g = 1 + 1/12
		TopicInterests interests = InterestFormula.interests(List.of(),
				List.of(added(Map.of("a", 1.0), T.minus(Duration.ofDays(30)))), T);

		assertThat(interests.weight("a")).isCloseTo(1.0833333333333333, EXACT);
	}

	@Test
	void signalsAfterTheReferenceTimeWeighAsNow() {
		// 기준 시각 하루 뒤 노출 · 좋아요 → 무게 1 → x = 1 ÷ 4 → g = 1.125
		TopicInterests interests = InterestFormula.interests(
				List.of(exposed(Set.of("a"), T.plus(Duration.ofDays(1)), FeedbackValue.LIKE)), List.of(), T);

		assertThat(interests.weight("a")).isCloseTo(1.125, EXACT);
	}

	@Test
	void theInterestIsClampedToOne() {
		// 노출 0 · 더함 4 → x = 4 ÷ 3 → 1 로 자름 → g = 1.5
		SubscriptionSignal add = added(Map.of("a", 1.0), T);
		TopicInterests interests = InterestFormula.interests(List.of(), List.of(add, add, add, add), T);

		assertThat(interests.weight("a")).isCloseTo(1.5, EXACT);
	}

	@Test
	void anArticleOnSeveralTopicsCountsForEachOfThem() {
		// 노출 {a, b} 하나 · 좋아요 → a · b 모두 x = 1 ÷ 4 → g = 1.125
		TopicInterests interests = InterestFormula
			.interests(List.of(exposed(Set.of("a", "b"), T, FeedbackValue.LIKE)), List.of(), T);

		assertThat(interests.weight("a")).isCloseTo(1.125, EXACT);
		assertThat(interests.weight("b")).isCloseTo(1.125, EXACT);
	}

	@Test
	void aShareScalesTheSubscriptionSignalAndARemovalSubtracts() {
		// 노출 1(없음) · 몫 0.5 의 뺌 → x = −0.5 ÷ 4 → g = 1 − 0.0625
		TopicInterests interests = InterestFormula.interests(List.of(exposed(Set.of("a"), T, FeedbackValue.NONE)),
				List.of(new SubscriptionSignal(Map.of("a", 0.5), -1, T)), T);

		assertThat(interests.weight("a")).isCloseTo(0.9375, EXACT);
	}

	private static ExposedArticle exposed(Set<String> subtopics, Instant at, FeedbackValue reaction) {
		return new ExposedArticle(subtopics, at, reaction);
	}

	private static SubscriptionSignal added(Map<String, Double> shares, Instant at) {
		return new SubscriptionSignal(shares, 1, at);
	}

}
