package net.yozmnews.digest.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.digest.domain.interest.TopicInterests;
import net.yozmnews.event.EventId;
import net.yozmnews.user.TopicSubscription;
import org.junit.jupiter.api.Test;

/**
 * 구독 필터 · 사건 순위 — 구독 행 대조는 사건의 주제(대분류 펼침 · 중분류), 지역 제외는 사건의 지역, 싫어요는 사건, 주제 없는 사건, 사건 점수(사건 중요도 =
 * 소속 최대 · low_confidence 0.7 · 관심도 가중치 · 중요도 없음 맨 뒤), 같은 점수면 후보 기사의 가장 늦은 발행 시각 · 사건 ID
 */
class SubscriptionFilterTests {

	@Test
	void anEventMatchesASubscribedCategoryThroughItsSubtopicsOrASubscribedSubtopic() {
		CandidateEvent executive = Facts.single(Facts.article(1).subtopics("politics.executive"));
		CandidateEvent markets = Facts.single(Facts.article(2).subtopics("economy.markets"));
		CandidateEvent finance = Facts.single(Facts.article(3).subtopics("economy.finance"));
		CandidateEvent several = Facts.single(Facts.article(4).subtopics("economy.finance", "politics.legislature"));

		assertThat(ids(SubscriptionFilter.select(List.of(executive, markets, finance, several),
				Facts.politicsAndMarkets(), TopicInterests.NEUTRAL_ALL)))
			.containsExactlyInAnyOrder(1L, 2L, 4L);
	}

	@Test
	void theSubscriptionIsMatchedOnTheTopicOfTheEventNotOnTheTopicOfItsArticlesInThePeriod() {
		// 기간에 든 기사는 금융(구독 밖)이지만 소속 셋 가운데 둘이 정치 행정이라 사건의 주제는 정치 행정이다
		CandidateEvent byMembers = Facts.event(10,
				List.of(Facts.article(1).subtopics("economy.finance"), Facts.article(2), Facts.article(3)),
				List.of(Facts.article(1).subtopics("economy.finance")));
		// 대조 — 기간에 든 기사는 정치 행정이지만 소속 셋 가운데 둘이 금융이라 사건의 주제는 금융이다
		CandidateEvent outside = Facts.event(11,
				List.of(Facts.article(4), Facts.article(5).subtopics("economy.finance"),
						Facts.article(6).subtopics("economy.finance")),
				List.of(Facts.article(4)));

		assertThat(ids(SubscriptionFilter.select(List.of(byMembers, outside), Facts.politicsAndMarkets(),
				TopicInterests.NEUTRAL_ALL)))
			.containsExactly(10L);
	}

	@Test
	void anEventWithoutTopicsHasNothingToMatch() {
		assertThat(SubscriptionFilter.select(List.of(Facts.single(Facts.article(1).unclassified())),
				Facts.politicsAndMarkets(), TopicInterests.NEUTRAL_ALL))
			.isEmpty();
	}

	@Test
	void anEventIsLeftOutWhenHalfOrMoreOfItsMembersAreInAnExcludedRegion() {
		UserFilter filter = UserFilter.of(Facts.interests(
				List.of(new TopicSubscription("politics", Set.of("politics.executive"))), Set.of("us"), Set.of(),
				Set.of()), Set.of());
		// 소속 둘 가운데 하나가 us — 몫 1/2 이라 사건의 지역에 든다
		CandidateEvent half = Facts.event(10, Facts.article(1).regions("us"), Facts.article(2));
		// 소속 셋 가운데 하나가 us — 사건의 지역이 아니라 남고, 그 기사도 후보 기사로 남는다
		CandidateEvent third = Facts.event(11, Facts.article(3).regions("us"), Facts.article(4), Facts.article(5));

		List<CandidateEvent> selected = SubscriptionFilter.select(List.of(half, third), filter,
				TopicInterests.NEUTRAL_ALL);

		assertThat(ids(selected)).containsExactly(11L);
		assertThat(selected.getFirst().articles()).extracting(article -> article.id().value())
			.containsExactly(3L, 4L, 5L);
	}

	@Test
	void aDislikedEventIsLeftOutWithAllItsArticles() {
		UserFilter filter = UserFilter.of(Facts.interests(
				List.of(new TopicSubscription("politics", Set.of("politics.executive"))), Set.of(), Set.of(), Set.of()),
				Set.of(new EventId(10)));
		CandidateEvent disliked = Facts.event(10, Facts.article(1), Facts.article(2));
		// 대조 — 싫어요가 아닌 사건은 남는다
		CandidateEvent kept = Facts.event(11, Facts.article(3));

		assertThat(ids(SubscriptionFilter.select(List.of(disliked, kept), filter, TopicInterests.NEUTRAL_ALL)))
			.containsExactly(11L);
	}

	@Test
	void theRankWeighsLowConfidenceAndPutsMissingImportanceLast() {
		CandidateEvent tagged3 = Facts.single(Facts.article(1).importance(3.0));
		CandidateEvent low4 = Facts.single(Facts.article(2).importance(4.0).lowConfidence());
		CandidateEvent tagged2 = Facts.single(Facts.article(3).importance(2.0));
		CandidateEvent none = Facts.single(Facts.article(4).importance(null));
		CandidateEvent tagged3Later = Facts.single(Facts.article(5).importance(3.0).published("2026-11-16T13:00:00Z"));
		CandidateEvent tagged3SameTimeLargerId = Facts.single(Facts.article(6).importance(3.0));

		// 3(13시) · 3(12시, 사건 1) · 3(12시, 사건 6) · 2.8(low 4) · 2 · 없음
		assertThat(ids(SubscriptionFilter.select(
				List.of(none, tagged2, low4, tagged3SameTimeLargerId, tagged3Later, tagged3),
				Facts.politicsAndMarkets(), TopicInterests.NEUTRAL_ALL)))
			.containsExactly(5L, 1L, 6L, 2L, 3L, 4L);
		assertThat(CandidateRank.score(low4, TopicInterests.NEUTRAL_ALL).orElseThrow()).isCloseTo(2.8, within(1e-9));
		assertThat(CandidateRank.score(none, TopicInterests.NEUTRAL_ALL)).isEmpty();
	}

	@Test
	void theEventImportanceIsTheMaximumOfItsMembersEvenOutsideThePeriod() {
		// 사건 10: 기간에 든 기사 중요도 2 · 기간 밖 소속 기사 중요도 4 → 사건 중요도 4. 사건 20: 기사 하나 중요도 3.5
		CandidateEvent wide = Facts.event(10, List.of(Facts.article(1).importance(2.0), Facts.article(2).importance(4.0)),
				List.of(Facts.article(1).importance(2.0)));
		CandidateEvent single = Facts.single(Facts.article(20).importance(3.5));

		assertThat(ids(SubscriptionFilter.select(List.of(single, wide), Facts.politicsAndMarkets(),
				TopicInterests.NEUTRAL_ALL)))
			.containsExactly(10L, 20L);
		assertThat(CandidateRank.score(wide, TopicInterests.NEUTRAL_ALL).orElseThrow()).isCloseTo(4.0, within(1e-9));
	}

	@Test
	void anEventIsLowConfidenceOnlyWhenAllItsMembersAre() {
		// 사건 10: 둘 다 low 4 → 2.8. 사건 11: low 4 하나 · tagged 1 하나 → 4. 사건 12: tagged 3.5
		CandidateEvent allLow = Facts.event(10, Facts.article(1).importance(4.0).lowConfidence(),
				Facts.article(2).importance(4.0).lowConfidence());
		CandidateEvent mixed = Facts.event(11, Facts.article(3).importance(4.0).lowConfidence(),
				Facts.article(4).importance(1.0));
		CandidateEvent tagged = Facts.single(Facts.article(12).importance(3.5));

		assertThat(ids(SubscriptionFilter.select(List.of(allLow, mixed, tagged), Facts.politicsAndMarkets(),
				TopicInterests.NEUTRAL_ALL)))
			.containsExactly(11L, 12L, 10L);
	}

	@Test
	void theTieBreakIsTheLatestArticleOfTheCandidateThenTheEventId() {
		// 같은 점수 3 — 사건 30 의 마지막 기사 14시, 사건 20 의 마지막 기사 13시, 사건 10 · 15 는 12시
		CandidateEvent ten = Facts.single(Facts.article(10));
		CandidateEvent fifteen = Facts.single(Facts.article(15));
		CandidateEvent twenty = Facts.event(20, Facts.article(21), Facts.article(22).published("2026-11-16T13:00:00Z"));
		CandidateEvent thirty = Facts.event(30, Facts.article(31).published("2026-11-16T14:00:00Z"), Facts.article(32));

		assertThat(ids(SubscriptionFilter.select(List.of(fifteen, ten, twenty, thirty), Facts.politicsAndMarkets(),
				TopicInterests.NEUTRAL_ALL)))
			.containsExactly(30L, 20L, 10L, 15L);
	}

	@Test
	void theInterestWeightMultipliesTheRank() {
		// 시장 가중치 1.5 — 3 × 1.5 = 4.5 가 4 × 1 = 4.0 앞, low 4 × 0.7 × 1.5 = 4.2 도 4.0 앞
		TopicInterests interests = new TopicInterests(Map.of("economy.markets", 1.5));
		CandidateEvent markets3 = Facts.single(Facts.article(1).subtopics("economy.markets").importance(3.0));
		CandidateEvent politics4 = Facts.single(Facts.article(2).importance(4.0));
		CandidateEvent marketsLow4 = Facts
			.single(Facts.article(3).subtopics("economy.markets").importance(4.0).lowConfidence());

		assertThat(ids(SubscriptionFilter.select(List.of(politics4, marketsLow4, markets3), Facts.politicsAndMarkets(),
				interests)))
			.containsExactly(1L, 3L, 2L);
		assertThat(CandidateRank.score(markets3, interests).orElseThrow()).isCloseTo(4.5, within(1e-9));
		assertThat(CandidateRank.score(marketsLow4, interests).orElseThrow()).isCloseTo(4.2, within(1e-9));
		assertThat(ids(SubscriptionFilter.select(List.of(politics4, marketsLow4, markets3), Facts.politicsAndMarkets(),
				TopicInterests.NEUTRAL_ALL)))
			.as("중립이면 중요도 순")
			.containsExactly(2L, 1L, 3L);
	}

	@Test
	void anEventOnSeveralTopicsIsWeightedByTheMeanOfItsTopics() {
		// 시장 1.5 · 정치 행정 1.0 의 평균 1.25 — 4 × 1.25 = 5.0
		TopicInterests interests = new TopicInterests(Map.of("economy.markets", 1.5));
		CandidateEvent both = Facts
			.single(Facts.article(1).subtopics("economy.markets", "politics.executive").importance(4.0));

		assertThat(CandidateRank.score(both, interests).orElseThrow()).isCloseTo(5.0, within(1e-9));
	}

	private static List<Long> ids(List<CandidateEvent> events) {
		return events.stream().map(event -> event.facts().id().value()).toList();
	}

}
