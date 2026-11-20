package net.yozmnews.digest.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventId;
import net.yozmnews.source.SourceId;
import net.yozmnews.user.TopicSubscription;
import org.junit.jupiter.api.Test;

/**
 * 후보 사건 꾸리기 — 사건 없는 기사 버림, 발행 시각 하한(기간 시작 − 24시간, 경계 포함), 기사 제외(형식 · 소스 — 지역 · 싫어요는 보지
 * 않음)는 기사마다, 값이 없는 사건 버림, 큰 판정 행 사건의 후보(사건 제외는 사건의 지역 · 싫어요, 지난 기사가 없으면 없음)
 * <p>
 * 지키는 규칙: digest-R17 · digest-R19
 */
class CandidateEventsTests {

	private static final Instant START = Instant.parse("2026-11-16T22:00:00Z");

	@Test
	void articlesWithoutAnEventAreNotCandidatesAndArticlesOfOneEventMakeOneCandidate() {
		ArticleFacts first = Facts.article(1).build();
		ArticleFacts second = Facts.article(2).build();
		ArticleFacts unassigned = Facts.article(3).build();

		List<CandidateEvent> events = CandidateEvents.subscribed(List.of(first, second, unassigned),
				Map.of(first.id(), new EventId(10), second.id(), new EventId(10)),
				Map.of(new EventId(10), Facts.eventFacts(10, List.of(Facts.article(1), Facts.article(2)))),
				Facts.politicsAndMarkets(), START);

		assertThat(events).hasSize(1);
		assertThat(events.getFirst().facts().id()).isEqualTo(new EventId(10));
		assertThat(ids(events.getFirst().articles())).containsExactly(1L, 2L);
	}

	@Test
	void anEventWithoutFactsIsLeftOut() {
		ArticleFacts article = Facts.article(1).build();

		assertThat(CandidateEvents.subscribed(List.of(article), Map.of(article.id(), new EventId(10)), Map.of(),
				Facts.politicsAndMarkets(), START))
			.isEmpty();
	}

	@Test
	void thePublishedLowerBoundIsTwentyFourHoursBeforeThePeriodStartInclusive() {
		ArticleFacts atBound = Facts.article(1).published("2026-11-15T22:00:00Z").build();
		ArticleFacts justBefore = Facts.article(2).published("2026-11-15T21:59:59Z").build();

		assertThat(articleIds(CandidateEvents.subscribed(List.of(atBound, justBefore), singleEvents(atBound, justBefore),
				facts(1, 2), Facts.politicsAndMarkets(), START)))
			.containsExactly(1L);
	}

	@Test
	void aPeriodStartingAtAnotherTimezonesMidnightMovesTheBoundWithIt() {
		// America/New_York 의 서머타임이 끝나는 날(2026-11-01)의 자정 = 04:00Z, 그 기간은 25시간이다 — 하한은 시작의 24시간 전 04:00Z
		Instant newYorkMidnight = Instant.parse("2026-11-01T04:00:00Z");
		ArticleFacts atBound = Facts.article(1).published("2026-10-31T04:00:00Z").build();
		ArticleFacts justBefore = Facts.article(2).published("2026-10-31T03:59:59Z").build();

		assertThat(articleIds(CandidateEvents.subscribed(List.of(atBound, justBefore), singleEvents(atBound, justBefore),
				facts(1, 2), Facts.politicsAndMarkets(), newYorkMidnight)))
			.containsExactly(1L);
	}

	@Test
	void formatsAndSourcesAreFilteredPerArticleButRegionsAndDislikesAreNot() {
		UserFilter filter = UserFilter.of(Facts.interests(
				List.of(new TopicSubscription("politics", Set.of("politics.executive"))), Set.of("us"),
				Set.of("brief"), Set.of(new SourceId(9))), Set.of(new EventId(10)));
		ArticleFacts plain = Facts.article(1).build();
		ArticleFacts inUs = Facts.article(2).regions("domestic", "us").build();
		ArticleFacts brief = Facts.article(3).formats("brief").build();
		ArticleFacts onlyFromNine = Facts.article(4).sources(9).build();
		ArticleFacts alsoFromTwo = Facts.article(5).sources(9, 2).build();
		ArticleFacts formatGone = Facts.article(6).formats().build();
		// 형식 키가 계보로 둘로 풀렸고 그 하나가 제외 — 하나라도 제외면 뺀다
		ArticleFacts formatSplitOneExcluded = Facts.article(7).formats("straight", "brief").build();
		List<ArticleFacts> all = List.of(plain, inUs, brief, onlyFromNine, alsoFromTwo, formatGone,
				formatSplitOneExcluded);
		Map<ArticleId, EventId> oneEvent = new java.util.HashMap<>();
		all.forEach(article -> oneEvent.put(article.id(), new EventId(10)));

		List<CandidateEvent> events = CandidateEvents.subscribed(all, oneEvent,
				Map.of(new EventId(10), Facts.eventFacts(10, List.of(Facts.article(1)))), filter, START);

		// 지역 us 가 붙은 2 는 남는다 — 지역 제외는 사건의 지역으로 본다. 싫어요인 사건 10 도 남는다 — 구독 필터가 사건으로 본다
		assertThat(articleIds(events)).containsExactly(1L, 2L, 5L, 6L);
	}

	@Test
	void anArticleWithoutSourcesDoesNotPass() {
		ArticleFacts noSource = new ArticleFacts(new ArticleId(1), Set.of("politics.executive"), Set.of(),
				Set.of("straight"), net.yozmnews.tag.TopicStatus.TAGGED, 3.0, Set.of(), Facts.PUBLISHED);

		assertThat(Facts.politicsAndMarkets().passesArticleExclusions(noSource)).isFalse();
		assertThat(Facts.politicsAndMarkets().passesArticleExclusions(Facts.article(2).build())).isTrue();
	}

	@Test
	void aBigEventKeepsItsPassingArticlesAndHasNoCandidateWhenItsRegionIsExcludedOrNothingPasses() {
		UserFilter filter = UserFilter.of(Facts.interests(List.of(), Set.of("us"), Set.of(), Set.of(new SourceId(9))),
				Set.of());
		EventFacts domestic = Facts.eventFacts(10, List.of(Facts.article(1)));
		ArticleFacts kept = Facts.article(1).build();
		ArticleFacts fromNine = Facts.article(2).sources(9).build();

		CandidateEvent big = CandidateEvents.big(domestic, List.of(kept, fromNine), filter);
		assertThat(big).isNotNull();
		assertThat(ids(big.articles())).containsExactly(1L);

		assertThat(CandidateEvents.big(domestic, List.of(fromNine), filter)).as("지난 기사가 없다").isNull();
		// 소속 둘 가운데 하나가 us — 몫 1/2 이라 사건의 지역에 us 가 든다
		EventFacts halfUs = Facts.eventFacts(11, List.of(Facts.article(3).regions("us"), Facts.article(4)));
		assertThat(CandidateEvents.big(halfUs, List.of(Facts.article(4).build()), filter)).as("사건의 지역이 제외").isNull();
	}

	@Test
	void aDislikedBigEventHasNoCandidate() {
		EventFacts domestic = Facts.eventFacts(10, List.of(Facts.article(1)));
		UserFilter disliking = UserFilter.of(Facts.interests(List.of(), Set.of(), Set.of(), Set.of()), Set.of())
			.withDisliked(Set.of(new EventId(10)));

		assertThat(CandidateEvents.big(domestic, List.of(Facts.article(1).build()), disliking)).isNull();
	}

	@Test
	void aDislikeOnAnotherEventLeavesTheBigEventIn() {
		// 대조 — 다른 사건의 싫어요는 이 사건을 빼지 않는다
		EventFacts domestic = Facts.eventFacts(10, List.of(Facts.article(1)));
		UserFilter disliking = UserFilter.of(Facts.interests(List.of(), Set.of(), Set.of(), Set.of()),
				Set.of(new EventId(11)));

		assertThat(CandidateEvents.big(domestic, List.of(Facts.article(1).build()), disliking)).isNotNull();
	}

	private static Map<ArticleId, EventId> singleEvents(ArticleFacts... articles) {
		Map<ArticleId, EventId> events = new java.util.HashMap<>();
		for (ArticleFacts article : articles) {
			events.put(article.id(), new EventId(article.id().value()));
		}
		return events;
	}

	private static Map<EventId, EventFacts> facts(long... ids) {
		Map<EventId, EventFacts> facts = new java.util.HashMap<>();
		for (long id : ids) {
			facts.put(new EventId(id), Facts.eventFacts(id, List.of(Facts.article(id))));
		}
		return facts;
	}

	private static List<Long> articleIds(List<CandidateEvent> events) {
		return events.stream().flatMap(event -> event.articles().stream()).map(article -> article.id().value()).toList();
	}

	private static List<Long> ids(List<ArticleFacts> articles) {
		return articles.stream().map(article -> article.id().value()).toList();
	}

}
