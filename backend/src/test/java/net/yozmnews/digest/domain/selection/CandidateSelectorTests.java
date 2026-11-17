package net.yozmnews.digest.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.candidate.SelectionReason;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigEvent;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigRow;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigSlots;
import net.yozmnews.event.EventId;
import org.junit.jupiter.api.Test;

/**
 * 선별의 조립 — 구독 필터부터 선정 이유까지 한 흐름. 후보 0건, 여러 태그에 걸친 기사, 사건 미배정 기사, 큰 슬롯과 같은 사건의 구독 후보,
 * 최대 후보 수 안에서 큰 슬롯이 먼저
 */
class CandidateSelectorTests {

	private static final Instant START = Instant.parse("2026-11-16T22:00:00Z");

	@Test
	void theWholeFlowPutsBigSlotArticlesFirstAndDropsTheirEventsFromTheSubscribed() {
		ArticleFacts politics = Facts.article(1).importance(4.0).build();
		ArticleFacts markets = Facts.article(2).subtopics("economy.markets").importance(3.0).build();
		ArticleFacts sameEventAsBig = Facts.article(3).importance(5.0).build();
		ArticleFacts unassigned = Facts.article(4).importance(2.0).build();
		ArticleFacts onTwoTopics = Facts.article(5).subtopics("politics.legislature", "economy.markets")
			.importance(1.0)
			.build();
		ArticleFacts outside = Facts.article(6).subtopics("sports.baseball").build();
		ArticleFacts bigArticle = Facts.article(7).subtopics("world.conflict").build();
		UserFilter filter = Facts.politicsAndMarkets();

		List<ArticleFacts> ranked = SubscriptionFilter
			.select(List.of(politics, markets, sameEventAsBig, unassigned, onTwoTopics, outside), filter, START);
		Map<ArticleId, net.yozmnews.event.EventId> eventOf = Map.of(politics.id(), new EventId(10), markets.id(),
				new EventId(20), sameEventAsBig.id(), new EventId(30), onTwoTopics.id(), new EventId(50));
		BigSlots big = BigSlotPicker.pick(
				List.of(new BigRow(List.of(new BigEvent(new EventId(30), new BigDecimal("12"), bigArticle)))), 3,
				SelectionRules.bigSeats(60));
		Shortlist shortlist = CandidateSelector.shortlist(ranked, eventOf, Map.of(), big, filter, 60);
		List<DigestCandidate> candidates = CandidateSelector.candidates(shortlist,
				Map.of(new ArticleId(1), 100, new ArticleId(2), 100, new ArticleId(4), 100, new ArticleId(5), 100,
						new ArticleId(7), 100),
				10_000);

		// 큰 슬롯 7 먼저, 구독은 순위대로 1(4.0) · 2(3.0) · 4(2.0, 미배정) · 5(1.0, 두 주제에 걸쳐 한 번). 3 은 큰 슬롯의 사건, 6 은 구독 밖
		assertThat(candidates).containsExactly(new DigestCandidate(new ArticleId(7), SelectionReason.MAJOR_STORYLINE),
				new DigestCandidate(new ArticleId(1), SelectionReason.SUBSCRIBED),
				new DigestCandidate(new ArticleId(2), SelectionReason.SUBSCRIBED),
				new DigestCandidate(new ArticleId(4), SelectionReason.SUBSCRIBED),
				new DigestCandidate(new ArticleId(5), SelectionReason.SUBSCRIBED));
	}

	@Test
	void theBigSlotTakesItsSeatsFirstWithinTheMaximum() {
		// 최대 4 · 큰 슬롯 상한 2 — 큰 슬롯 2 다음 구독 2
		List<ArticleFacts> ranked = List.of(Facts.article(1).build(), Facts.article(2).build(),
				Facts.article(3).build());
		BigSlots big = BigSlotPicker.pick(List.of(new BigRow(List.of(
				new BigEvent(new EventId(91), BigDecimal.TEN, Facts.article(91).build()),
				new BigEvent(new EventId(92), BigDecimal.ONE, Facts.article(92).build()),
				new BigEvent(new EventId(93), BigDecimal.ZERO, Facts.article(93).build())))), 3,
				SelectionRules.bigSeats(4));

		Shortlist shortlist = CandidateSelector.shortlist(ranked, Map.of(), Map.of(), big, Facts.politicsAndMarkets(),
				4);

		assertThat(shortlist.big()).extracting(article -> article.id().value()).containsExactly(91L, 92L);
		assertThat(shortlist.subscribed()).extracting(article -> article.id().value()).containsExactly(1L, 2L);
	}

	@Test
	void noSubscriptionsAndNoBigStorylinesGiveNoCandidates() {
		UserFilter nothing = UserFilter.of(Facts.interests(List.of(), java.util.Set.of(), java.util.Set.of(),
				java.util.Set.of()));
		List<ArticleFacts> ranked = SubscriptionFilter.select(List.of(Facts.article(1).build()), nothing, START);

		Shortlist shortlist = CandidateSelector.shortlist(ranked, Map.of(), Map.of(), BigSlots.NONE, nothing, 60);

		assertThat(ranked).isEmpty();
		assertThat(CandidateSelector.candidates(shortlist, Map.of(), 10_000)).isEmpty();

		// 구독이 없어도 큰 슬롯은 들어간다
		BigSlots big = BigSlotPicker.pick(
				List.of(new BigRow(List.of(new BigEvent(new EventId(40), BigDecimal.ONE, Facts.article(8).build())))),
				3, SelectionRules.bigSeats(60));
		Shortlist bigOnly = CandidateSelector.shortlist(ranked, Map.of(), Map.of(), big, nothing, 60);
		assertThat(CandidateSelector.candidates(bigOnly, Map.of(new ArticleId(8), 100), 10_000))
			.containsExactly(new DigestCandidate(new ArticleId(8), SelectionReason.MAJOR_STORYLINE));
	}

}
