package net.yozmnews.digest.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.interest.InterestWeights;
import net.yozmnews.digest.domain.interest.StorylineInterests;
import net.yozmnews.digest.domain.interest.TopicInterests;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigEvent;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigRow;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigSlots;
import net.yozmnews.event.EventId;
import net.yozmnews.storyline.StorylineId;
import org.junit.jupiter.api.Test;

/**
 * 선별의 조립 — 후보 사건부터 선정 이유까지 한 흐름. 후보 0건, 여러 태그에 걸친 사건, 사건 없는 기사, 큰 슬롯과 같은 사건의 구독 후보, 최대
 * 후보 수(사건 수) 안에서 큰 슬롯이 먼저
 */
class CandidateSelectorTests {

	/** 거른 꼭대기가 아닌 큰 판정 행의 머리 */
	private static final StorylineId HEAD = new StorylineId(500);

	private static final Instant START = Instant.parse("2026-11-16T22:00:00Z");

	@Test
	void theWholeFlowPutsBigSlotEventsFirstAndDropsThemFromTheSubscribed() {
		Facts.Builder politicsArticle = Facts.article(1).importance(4.0);
		Facts.Builder marketsArticle = Facts.article(2).subtopics("economy.markets").importance(3.0);
		Facts.Builder conflictArticle = Facts.article(3).subtopics("world.conflict").importance(5.0);
		Facts.Builder twoTopicsArticle = Facts.article(5)
			.subtopics("politics.legislature", "economy.markets")
			.importance(1.0);
		Facts.Builder baseballArticle = Facts.article(6).subtopics("sports.baseball");
		ArticleFacts politics = politicsArticle.build();
		ArticleFacts markets = marketsArticle.build();
		ArticleFacts sameEventAsBig = conflictArticle.build();
		ArticleFacts unassigned = Facts.article(4).importance(2.0).build();
		ArticleFacts onTwoTopics = twoTopicsArticle.build();
		ArticleFacts outside = baseballArticle.build();
		UserFilter filter = Facts.politicsAndMarkets();
		// 사건 30 은 정치 행정 기사 둘 · 분쟁 기사 하나라 사건의 주제가 정치 행정 — 구독 사건이기도 하지만 큰 슬롯이 쓴다
		Map<EventId, EventFacts> facts = Map.of(new EventId(10), Facts.eventFacts(10, List.of(politicsArticle)),
				new EventId(20), Facts.eventFacts(20, List.of(marketsArticle)), new EventId(30),
				Facts.eventFacts(30, List.of(conflictArticle, Facts.article(31), Facts.article(32))), new EventId(50),
				Facts.eventFacts(50, List.of(twoTopicsArticle)), new EventId(60),
				Facts.eventFacts(60, List.of(baseballArticle)));
		Map<ArticleId, EventId> eventOf = Map.of(politics.id(), new EventId(10), markets.id(), new EventId(20),
				sameEventAsBig.id(), new EventId(30), onTwoTopics.id(), new EventId(50), outside.id(), new EventId(60));

		List<CandidateEvent> pool = CandidateEvents.subscribed(
				List.of(politics, markets, sameEventAsBig, unassigned, onTwoTopics, outside), eventOf, facts, Map.of(),
				filter, START);
		List<CandidateEvent> ranked = SubscriptionFilter.select(pool, filter, InterestWeights.NEUTRAL_ALL);
		CandidateEvent bigEvent = pool.stream().filter(event -> event.facts().id().value() == 30).findFirst().orElseThrow();
		BigSlots big = BigSlotPicker.pick(
				List.of(new BigRow(HEAD, List.of(new BigEvent(new EventId(30), new BigDecimal("12"), bigEvent)))), 3,
				SelectionRules.bigSeats(60), filter);
		Shortlist shortlist = CandidateSelector.shortlist(ranked, big, filter, 60);
		Shortlist cut = BudgetCut.apply(shortlist, Map.of(new EventId(10), 100, new EventId(20), 100,
				new EventId(30), 100, new EventId(50), 100), 10_000);
		List<SelectedEvent> selected = CandidateSelector.candidates(cut, cut, InterestWeights.NEUTRAL_ALL);

		// 큰 슬롯 사건 30 먼저(행 0), 구독은 순위대로 10(4.0) · 20(3.0) · 50(1.0, 두 주제에 걸쳐 한 번). 4 는 사건이 없고 60 은 구독 밖
		assertThat(selected).extracting(event -> event.event().facts().id().value(), SelectedEvent::reason,
				SelectedEvent::bigRow)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(30L, SelectionReason.MAJOR_STORYLINE, 0),
					org.assertj.core.groups.Tuple.tuple(10L, SelectionReason.SUBSCRIBED, null),
					org.assertj.core.groups.Tuple.tuple(20L, SelectionReason.SUBSCRIBED, null),
					org.assertj.core.groups.Tuple.tuple(50L, SelectionReason.SUBSCRIBED, null));
	}

	@Test
	void theBigSlotTakesItsSeatsFirstWithinTheMaximum() {
		// 최대 4 · 큰 슬롯 상한 2 — 큰 슬롯 2 다음 구독 2
		List<CandidateEvent> ranked = List.of(Facts.single(Facts.article(1)), Facts.single(Facts.article(2)),
				Facts.single(Facts.article(3)));
		BigSlots big = BigSlotPicker.pick(
				List.of(new BigRow(HEAD, List.of(new BigEvent(new EventId(91), BigDecimal.TEN, Facts.single(Facts.article(91))),
						new BigEvent(new EventId(92), BigDecimal.ONE, Facts.single(Facts.article(92))),
						new BigEvent(new EventId(93), BigDecimal.ZERO, Facts.single(Facts.article(93)))))),
				3, SelectionRules.bigSeats(4), Facts.politicsAndMarkets());

		Shortlist shortlist = CandidateSelector.shortlist(ranked, big, Facts.politicsAndMarkets(), 4);

		assertThat(shortlist.big()).extracting(event -> event.facts().id().value()).containsExactly(91L, 92L);
		assertThat(shortlist.subscribed()).extracting(event -> event.facts().id().value()).containsExactly(1L, 2L);
	}

	@Test
	void noSubscriptionsAndNoBigStorylinesGiveNoCandidates() {
		UserFilter nothing = UserFilter.of(Facts.interests(List.of(), Set.of(), Set.of(), Set.of()), Set.of(),
				Set.of());
		List<CandidateEvent> ranked = SubscriptionFilter.select(List.of(Facts.single(Facts.article(1))), nothing,
				InterestWeights.NEUTRAL_ALL);

		Shortlist shortlist = CandidateSelector.shortlist(ranked, BigSlots.NONE, nothing, 60);

		assertThat(ranked).isEmpty();
		Shortlist cut = BudgetCut.apply(shortlist, Map.of(), 10_000);
		assertThat(CandidateSelector.candidates(cut, cut, InterestWeights.NEUTRAL_ALL)).isEmpty();

		// 구독이 없어도 큰 슬롯은 들어간다
		BigSlots big = BigSlotPicker.pick(
				List.of(new BigRow(HEAD, List.of(new BigEvent(new EventId(40), BigDecimal.ONE, Facts.single(Facts.article(40)))))),
				3, SelectionRules.bigSeats(60), nothing);
		Shortlist bigOnly = CandidateSelector.shortlist(ranked, big, nothing, 60);
		Shortlist bigOnlyCut = BudgetCut.apply(bigOnly, Map.of(new EventId(40), 100), 10_000);
		assertThat(CandidateSelector.candidates(bigOnlyCut, bigOnlyCut, InterestWeights.NEUTRAL_ALL))
			.extracting(event -> event.event().facts().id().value(), SelectedEvent::reason)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(40L, SelectionReason.MAJOR_STORYLINE));
	}

	@Test
	void anEventThatEntersOnlyByInterestIsMarkedInterest() {
		// 자리 1 — 관심도: 시장 3 × 1.5 = 4.5 가 정치 4 × 1 = 4.0 앞이라 시장, 중립: 정치 4 가 시장 3 앞이라 정치
		InterestWeights interests = new InterestWeights(new TopicInterests(Map.of("economy.markets", 1.5)),
				StorylineInterests.NEUTRAL_ALL);
		CandidateEvent markets = Facts.single(Facts.article(1).subtopics("economy.markets").importance(3.0));
		CandidateEvent politics = Facts.single(Facts.article(2).importance(4.0));

		assertThat(select(List.of(markets, politics), interests, 1)).containsExactly(Map.entry(1L, SelectionReason.INTEREST));
	}

	@Test
	void anEventThatWouldEnterWithoutInterestStaysSubscribed() {
		// 자리 2 — 중립으로도 둘 다 든다
		InterestWeights interests = new InterestWeights(new TopicInterests(Map.of("economy.markets", 1.5)),
				StorylineInterests.NEUTRAL_ALL);
		CandidateEvent markets = Facts.single(Facts.article(1).subtopics("economy.markets").importance(3.0));
		CandidateEvent politics = Facts.single(Facts.article(2).importance(4.0));

		assertThat(select(List.of(markets, politics), interests, 2)).containsExactly(
				Map.entry(1L, SelectionReason.SUBSCRIBED), Map.entry(2L, SelectionReason.SUBSCRIBED));
	}

	@Test
	void anEventThatEntersBecauseAnotherSankIsNotMarkedInterest() {
		// 자리 1 — 관심도: 정치 4 × 0.5 = 2.0 이 시장 3 × 1 = 3.0 뒤라 시장, 중립: 정치. 시장의 가중치는 1 이라 관심도로 오른 사건이 아니다
		InterestWeights interests = new InterestWeights(new TopicInterests(Map.of("politics.executive", 0.5)),
				StorylineInterests.NEUTRAL_ALL);
		CandidateEvent markets = Facts.single(Facts.article(1).subtopics("economy.markets").importance(3.0));
		CandidateEvent politics = Facts.single(Facts.article(2).importance(4.0));

		assertThat(select(List.of(markets, politics), interests, 1))
			.containsExactly(Map.entry(1L, SelectionReason.SUBSCRIBED));
	}

	@Test
	void anEventRaisedOnlyByItsStorylineIsMarkedInterestAndAProductAtOrBelowOneIsNot() {
		// 자리 1 — 꼭대기 100 의 가중치 1.5: 시장 3 × 1 × 1.5 = 4.5 가 정치 4.0 앞. 주제 가중치는 1 이어도 곱이 1 보다 커 INTEREST
		StorylineId tree = new StorylineId(100);
		InterestWeights byStoryline = new InterestWeights(TopicInterests.NEUTRAL_ALL,
				new StorylineInterests(Map.of(tree, 1.5)));
		CandidateEvent markets = Facts.withTops(Facts.single(Facts.article(1).subtopics("economy.markets").importance(3.0)),
				tree);
		CandidateEvent politics = Facts.single(Facts.article(2).importance(4.0));
		// 대조 — 주제 1.25 × 꼭대기 0.75 = 0.9375 는 1 이하: 시장 3 × 0.9375 = 2.8125 가 정치 4.0 뒤라 정치(중립과 같음)
		InterestWeights mixed = new InterestWeights(new TopicInterests(Map.of("economy.markets", 1.25)),
				new StorylineInterests(Map.of(tree, 0.75)));
		assertThat(select(List.of(markets, politics), mixed, 1)).containsExactly(Map.entry(2L, SelectionReason.SUBSCRIBED));
		// 대조 — 한쪽 가중치만 1 보다 커도 곱이 1 이하면 INTEREST 가 아니다: 정치가 주제 0.5 로 4.0 × 0.5 = 2.0 이 되어 시장
		// 3 × 1.25 × 0.75 = 2.8125 가 중립 결과에 없이 들지만, 곱 0.9375 는 1 이하라 SUBSCRIBED
		InterestWeights demoted = new InterestWeights(
				new TopicInterests(Map.of("economy.markets", 1.25, "politics.executive", 0.5)),
				new StorylineInterests(Map.of(tree, 0.75)));
		assertThat(select(List.of(markets, politics), demoted, 1))
			.containsExactly(Map.entry(1L, SelectionReason.SUBSCRIBED));

		assertThat(select(List.of(markets, politics), byStoryline, 1))
			.containsExactly(Map.entry(1L, SelectionReason.INTEREST));
	}

	@Test
	void aBigRowBelongsOnlyToABigSlotEvent() {
		CandidateEvent event = Facts.single(Facts.article(1));

		assertThatIllegalArgumentException()
			.isThrownBy(() -> new SelectedEvent(event, SelectionReason.MAJOR_STORYLINE, null));
		assertThatIllegalArgumentException().isThrownBy(() -> new SelectedEvent(event, SelectionReason.SUBSCRIBED, 0));
	}

	/** 관심도 순위와 중립 순위로 각각 고르고 견준다 — 큰 슬롯 없음, 토큰은 넉넉하다. 사건 ID → 선정 이유 */
	private static List<Map.Entry<Long, SelectionReason>> select(List<CandidateEvent> events, InterestWeights interests,
			int max) {
		UserFilter filter = Facts.politicsAndMarkets();
		Shortlist ranked = CandidateSelector.shortlist(SubscriptionFilter.select(events, filter, interests),
				BigSlots.NONE, filter, max);
		Shortlist neutral = CandidateSelector.shortlist(
				SubscriptionFilter.select(events, filter, InterestWeights.NEUTRAL_ALL), BigSlots.NONE, filter, max);
		Map<EventId, Integer> tokens = Map.of(new EventId(1), 100, new EventId(2), 100);
		return CandidateSelector
			.candidates(BudgetCut.apply(ranked, tokens, 10_000), BudgetCut.apply(neutral, tokens, 10_000), interests)
			.stream()
			.map(event -> Map.entry(event.event().facts().id().value(), event.reason()))
			.toList();
	}

}
