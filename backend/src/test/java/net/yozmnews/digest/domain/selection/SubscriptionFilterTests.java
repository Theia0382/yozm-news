package net.yozmnews.digest.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.interest.TopicInterests;
import net.yozmnews.source.SourceId;
import net.yozmnews.user.TopicSubscription;
import org.junit.jupiter.api.Test;

/**
 * 구독 필터 · 순위 · 사용자 제외 — 발행 시각 하한(기간 시작 − 24시간, 경계 포함), 구독 행 대조(대분류 펼침 · 중분류), 지역 · 형식 · 소스
 * 제외(소스는 모두 제외일 때만), 미분류, 순위(low_confidence 0.7 · 중요도 없음 맨 뒤 · 발행 시각 · 기사 ID)
 */
class SubscriptionFilterTests {

	private static final Instant START = Instant.parse("2026-11-16T22:00:00Z");

	@Test
	void thePublishedLowerBoundIsTwentyFourHoursBeforeThePeriodStartInclusive() {
		ArticleFacts atBound = Facts.article(1).published("2026-11-15T22:00:00Z").build();
		ArticleFacts justBefore = Facts.article(2).published("2026-11-15T21:59:59Z").build();

		assertThat(ids(SubscriptionFilter.select(List.of(atBound, justBefore), Facts.politicsAndMarkets(), START,
				TopicInterests.NEUTRAL_ALL)))
			.containsExactly(1L);
	}

	@Test
	void aPeriodStartingAtAnotherTimezonesMidnightMovesTheBoundWithIt() {
		// America/New_York 의 서머타임이 끝나는 날(2026-11-01)의 자정 = 04:00Z, 그 기간은 25시간이다 — 하한은 시작의 24시간 전 04:00Z
		Instant newYorkMidnight = Instant.parse("2026-11-01T04:00:00Z");
		ArticleFacts atBound = Facts.article(1).published("2026-10-31T04:00:00Z").build();
		ArticleFacts justBefore = Facts.article(2).published("2026-10-31T03:59:59Z").build();

		assertThat(ids(SubscriptionFilter.select(List.of(atBound, justBefore), Facts.politicsAndMarkets(),
				newYorkMidnight, TopicInterests.NEUTRAL_ALL)))
			.containsExactly(1L);
	}

	@Test
	void anArticleMatchesASubscribedCategoryThroughItsSubtopicsOrASubscribedSubtopic() {
		ArticleFacts executive = Facts.article(1).subtopics("politics.executive").build();
		ArticleFacts markets = Facts.article(2).subtopics("economy.markets").build();
		ArticleFacts finance = Facts.article(3).subtopics("economy.finance").build();
		ArticleFacts several = Facts.article(4).subtopics("economy.finance", "politics.legislature").build();

		assertThat(ids(SubscriptionFilter.select(List.of(executive, markets, finance, several),
				Facts.politicsAndMarkets(), START, TopicInterests.NEUTRAL_ALL)))
			.containsExactlyInAnyOrder(1L, 2L, 4L);
	}

	@Test
	void unclassifiedArticlesHaveNoTopicToMatch() {
		assertThat(SubscriptionFilter.select(List.of(Facts.article(1).unclassified().build()),
				Facts.politicsAndMarkets(), START, TopicInterests.NEUTRAL_ALL))
			.isEmpty();
	}

	@Test
	void excludedRegionsFormatsAndSourcesAreLeftOut() {
		UserFilter filter = UserFilter.of(Facts.interests(
				List.of(new TopicSubscription("politics", Set.of("politics.executive"))), Set.of("us"),
				Set.of("brief"), Set.of(new SourceId(9))));
		ArticleFacts plain = Facts.article(1).build();
		ArticleFacts inUs = Facts.article(2).regions("domestic", "us").build();
		ArticleFacts brief = Facts.article(3).formats("brief").build();
		ArticleFacts onlyFromNine = Facts.article(4).sources(9).build();
		ArticleFacts alsoFromTwo = Facts.article(5).sources(9, 2).build();
		ArticleFacts formatGone = Facts.article(6).formats().build();
		// 형식 키가 계보로 둘로 풀렸고 그 하나가 제외 — 지역과 같이 하나라도 제외면 뺀다
		ArticleFacts formatSplitOneExcluded = Facts.article(7).formats("straight", "brief").build();

		assertThat(ids(SubscriptionFilter.select(List.of(plain, inUs, brief, onlyFromNine, alsoFromTwo, formatGone,
				formatSplitOneExcluded),
				filter, START, TopicInterests.NEUTRAL_ALL)))
			.containsExactlyInAnyOrder(1L, 5L, 6L);
	}

	@Test
	void anArticleWithoutSourcesDoesNotPass() {
		ArticleFacts noSource = new ArticleFacts(new ArticleId(1), Set.of("politics.executive"), Set.of(),
				Set.of("straight"), net.yozmnews.tag.TopicStatus.TAGGED, 3.0, Set.of(), Facts.PUBLISHED, false);

		assertThat(Facts.politicsAndMarkets().passesExclusions(noSource)).isFalse();
	}

	@Test
	void theRankWeighsLowConfidenceAndPutsMissingImportanceLast() {
		ArticleFacts tagged3 = Facts.article(1).importance(3.0).build();
		ArticleFacts low4 = Facts.article(2).importance(4.0).lowConfidence().build();
		ArticleFacts tagged2 = Facts.article(3).importance(2.0).build();
		ArticleFacts none = Facts.article(4).importance(null).build();
		ArticleFacts tagged3Later = Facts.article(5).importance(3.0).published("2026-11-16T13:00:00Z").build();
		ArticleFacts tagged3SameTimeLargerId = Facts.article(6).importance(3.0).build();

		// 3(13시) · 3(12시, ID 1) · 3(12시, ID 6) · 2.8(low 4) · 2 · 없음
		assertThat(ids(SubscriptionFilter.select(
				List.of(none, tagged2, low4, tagged3SameTimeLargerId, tagged3Later, tagged3),
				Facts.politicsAndMarkets(), START, TopicInterests.NEUTRAL_ALL)))
			.containsExactly(5L, 1L, 6L, 2L, 3L, 4L);
		assertThat(CandidateRank.score(low4, TopicInterests.NEUTRAL_ALL).orElseThrow()).isCloseTo(2.8, within(1e-9));
		assertThat(CandidateRank.score(none, TopicInterests.NEUTRAL_ALL)).isEmpty();
	}

	@Test
	void theInterestWeightMultipliesTheRank() {
		// 시장 가중치 1.5 — 3 × 1.5 = 4.5 가 4 × 1 = 4.0 앞, low 4 × 0.7 × 1.5 = 4.2 도 4.0 앞
		TopicInterests interests = new TopicInterests(Map.of("economy.markets", 1.5));
		ArticleFacts markets3 = Facts.article(1).subtopics("economy.markets").importance(3.0).build();
		ArticleFacts politics4 = Facts.article(2).importance(4.0).build();
		ArticleFacts marketsLow4 = Facts.article(3)
			.subtopics("economy.markets")
			.importance(4.0)
			.lowConfidence()
			.build();

		assertThat(ids(SubscriptionFilter.select(List.of(politics4, marketsLow4, markets3), Facts.politicsAndMarkets(),
				START, interests)))
			.containsExactly(1L, 3L, 2L);
		assertThat(CandidateRank.score(markets3, interests).orElseThrow()).isCloseTo(4.5, within(1e-9));
		assertThat(CandidateRank.score(marketsLow4, interests).orElseThrow()).isCloseTo(4.2, within(1e-9));
		assertThat(ids(SubscriptionFilter.select(List.of(politics4, marketsLow4, markets3), Facts.politicsAndMarkets(),
				START, TopicInterests.NEUTRAL_ALL)))
			.as("중립이면 중요도 순")
			.containsExactly(2L, 1L, 3L);
	}

	@Test
	void anArticleOnSeveralTopicsIsWeightedByTheMeanOfItsTopics() {
		// 시장 1.5 · 정치 행정 1.0 의 평균 1.25 — 4 × 1.25 = 5.0
		TopicInterests interests = new TopicInterests(Map.of("economy.markets", 1.5));
		ArticleFacts both = Facts.article(1).subtopics("economy.markets", "politics.executive").importance(4.0).build();

		assertThat(CandidateRank.score(both, interests).orElseThrow()).isCloseTo(5.0, within(1e-9));
	}

	@Test
	void aDislikedArticleDoesNotPass() {
		ArticleFacts disliked = Facts.article(1).disliked().build();
		ArticleFacts kept = Facts.article(2).build();

		assertThat(ids(SubscriptionFilter.select(List.of(disliked, kept), Facts.politicsAndMarkets(), START,
				TopicInterests.NEUTRAL_ALL)))
			.containsExactly(2L);
		assertThat(Facts.politicsAndMarkets().passesExclusions(disliked)).as("큰 슬롯 대표의 자격도 같은 판정").isFalse();
		assertThat(Facts.politicsAndMarkets().passesExclusions(kept)).isTrue();
	}

	private static List<Long> ids(List<ArticleFacts> articles) {
		return articles.stream().map(article -> article.id().value()).toList();
	}

}
