package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 구독 제안 판정 — 기준 0.35 의 경계, 구독한 중분류 제외, 음의 관심 · 신호 없음, 같은 관심의 체계 순, 거절 뒤 90일과 "거절 때보다 큼"의 경계,
 * 거절 때 관심이 상한 1 인 경우, 순서 표에 없는 키. 기준 · 기간은 상수 이름이 아니라 손으로 적은 리터럴(0.35 · 90일)로 고정한다.
 * <p>
 * 지키는 규칙: digest-R41
 */
class TopicSuggestionRuleTests {

	private static final Instant NOW = Instant.parse("2026-11-18T03:00:00Z");

	private static final Duration PERIOD = Duration.ofDays(90);

	private static final Map<String, Integer> ORDER = Map.of("economy.markets", 0, "economy.industry", 1,
			"politics.executive", 2, "politics.legislature", 3, "society.labor", 4);

	@Test
	void unsubscribedSubtopicsAtOrAboveTheThresholdAreSuggestedHighestFirst() {
		InterestLevels levels = new InterestLevels(Map.of("economy.markets", 0.35, "economy.industry", 0.3499,
				"politics.executive", 0.5, "politics.legislature", 0.9, "society.labor", -0.6));

		assertThat(TopicSuggestionRule.suggest(levels, Set.of("politics.legislature"), Map.of(), ORDER, NOW))
			.containsExactly("politics.executive", "economy.markets");
	}

	@Test
	void noSignalsSuggestNothing() {
		assertThat(TopicSuggestionRule.suggest(InterestLevels.NONE, Set.of(), Map.of(), ORDER, NOW)).isEmpty();
	}

	@Test
	void equalLevelsFollowTheTagSetOrderNotTheKeys() {
		// 키 사전순(politics.executive < society.labor)과 반대인 체계 순서를 넣는다
		Map<String, Integer> order = Map.of("society.labor", 0, "politics.executive", 1);
		InterestLevels levels = new InterestLevels(Map.of("politics.executive", 0.4, "society.labor", 0.4));

		assertThat(TopicSuggestionRule.suggest(levels, Set.of(), Map.of(), order, NOW))
			.containsExactly("society.labor", "politics.executive");
	}

	@Test
	void aDeclineBlocksUntilNinetyDaysHavePassed() {
		InterestLevels levels = new InterestLevels(Map.of("economy.markets", 0.6));
		Instant oneSecondShort = NOW.minus(PERIOD).plusSeconds(1);

		assertThat(TopicSuggestionRule.suggest(levels, Set.of(),
				Map.of("economy.markets", new DeclinedTopic(0.1, oneSecondShort)), ORDER, NOW))
			.as("90일 − 1초 — 관심이 커도 막는다")
			.isEmpty();
		assertThat(TopicSuggestionRule.suggest(levels, Set.of(),
				Map.of("economy.markets", new DeclinedTopic(0.5, NOW.minus(PERIOD))), ORDER, NOW))
			.as("90일 정각 · 거절 때보다 크다")
			.containsExactly("economy.markets");
	}

	@Test
	void afterTheDeclinePeriodTheLevelMustBeHigherThanAtTheDecline() {
		InterestLevels levels = new InterestLevels(Map.of("economy.markets", 0.6));
		Instant longAgo = NOW.minus(Duration.ofDays(100));

		assertThat(TopicSuggestionRule.suggest(levels, Set.of(),
				Map.of("economy.markets", new DeclinedTopic(0.6, longAgo)), ORDER, NOW))
			.as("같으면 내지 않는다")
			.isEmpty();
		assertThat(TopicSuggestionRule.suggest(levels, Set.of(),
				Map.of("economy.markets", new DeclinedTopic(0.7, longAgo)), ORDER, NOW))
			.as("작으면 내지 않는다")
			.isEmpty();
		assertThat(TopicSuggestionRule.suggest(levels, Set.of(),
				Map.of("economy.markets", new DeclinedTopic(0.59, longAgo)), ORDER, NOW))
			.as("크면 낸다")
			.containsExactly("economy.markets");
	}

	@Test
	void aDeclineAtTheUpperBoundNeverLetsTheSuggestionBack() {
		InterestLevels levels = new InterestLevels(Map.of("economy.markets", 1.0));

		assertThat(TopicSuggestionRule.suggest(levels, Set.of(),
				Map.of("economy.markets", new DeclinedTopic(1.0, NOW.minus(Duration.ofDays(1_000)))), ORDER, NOW))
			.isEmpty();
	}

	@Test
	void aDeclineOfAnotherSubtopicDoesNotBlock() {
		InterestLevels levels = new InterestLevels(Map.of("economy.markets", 0.6));

		assertThat(TopicSuggestionRule.suggest(levels, Set.of(),
				Map.of("economy.industry", new DeclinedTopic(0.1, NOW)), ORDER, NOW))
			.containsExactly("economy.markets");
	}

	@Test
	void aSuggestedSubtopicMissingFromTheOrderIsRefused() {
		InterestLevels levels = new InterestLevels(Map.of("sports.nowhere", 0.6));

		assertThatIllegalArgumentException()
			.isThrownBy(() -> TopicSuggestionRule.suggest(levels, Set.of(), Map.of(), ORDER, NOW));
	}

}
