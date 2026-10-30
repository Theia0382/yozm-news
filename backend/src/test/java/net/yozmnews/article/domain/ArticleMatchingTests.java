package net.yozmnews.article.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import net.yozmnews.article.ArticleId;
import net.yozmnews.article.domain.MatchCandidates.GuidCandidate;
import net.yozmnews.article.domain.MatchCandidates.TitleCandidate;
import net.yozmnews.source.ArticleFetched;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FetchedArticle;
import net.yozmnews.source.SourceId;
import org.junit.jupiter.api.Test;

/**
 * 같은 기사 판정 — 표에 적지 않은 후보는 비어 있다. 기준 발행 시각은 {@code 2026-10-28T00:00:00Z} 다.
 * <p>
 * 지키는 규칙: article-R2 · article-R3 · article-R4 · article-R5
 */
class ArticleMatchingTests {

	private static final Instant PUBLISHED_AT = Instant.parse("2026-10-28T00:00:00Z");

	private static final String TITLE = "국회 본회의 통과";

	/** {@code TITLE} 의 키 "국회본회의통과" 와 같은 키가 되는 제목 */
	private static final KeyHash SAME_TITLE = TitleKey.of("국회  본회의 통과").hash();

	private static final KeyHash OTHER_TITLE = TitleKey.of("국회 본회의 부결").hash();

	@Test
	void articleUrlComesFirst() {
		MatchCandidates candidates = new MatchCandidates(id(7), id(9), List.of(guid(5, SAME_TITLE)),
				List.of(title(4, "2026-10-28T00:00:00Z", false)));

		assertThat(decide(TITLE, candidates)).isEqualTo(existing(7, MatchKind.URL));
	}

	@Test
	void entryUrlComesBeforeGuidAndTitle() {
		MatchCandidates candidates = new MatchCandidates(null, id(9), List.of(guid(5, SAME_TITLE)),
				List.of(title(3, "2026-10-28T00:00:00Z", false)));

		assertThat(decide(TITLE, candidates)).isEqualTo(existing(9, MatchKind.URL));
	}

	@Test
	void guidComesBeforeTitle() {
		MatchCandidates candidates = new MatchCandidates(null, null, List.of(guid(5, SAME_TITLE)),
				List.of(title(3, "2026-10-28T00:00:00Z", false)));

		assertThat(decide(TITLE, candidates)).isEqualTo(existing(5, MatchKind.GUID));
	}

	@Test
	void guidTieGoesToTheSmallestId() {
		MatchCandidates candidates = new MatchCandidates(null, null,
				List.of(guid(8, SAME_TITLE), guid(5, SAME_TITLE)), List.of());

		assertThat(decide(TITLE, candidates)).isEqualTo(existing(5, MatchKind.GUID));
	}

	@Test
	void reusedGuidWithAnotherTitleIsANewArticle() {
		MatchCandidates candidates = new MatchCandidates(null, null, List.of(guid(5, OTHER_TITLE)), List.of());

		assertThat(decide(TITLE, candidates)).isEqualTo(new MatchDecision.New(true));
	}

	@Test
	void reusedGuidFallsThroughToTitle() {
		MatchCandidates candidates = new MatchCandidates(null, null, List.of(guid(5, OTHER_TITLE)),
				List.of(title(4, "2026-10-28T01:00:00Z", false)));

		assertThat(decide(TITLE, candidates)).isEqualTo(existing(4, MatchKind.TITLE));
	}

	@Test
	void titleWindowIncludesItsBoundary() {
		MatchCandidates candidates = new MatchCandidates(null, null, List.of(),
				List.of(title(4, "2026-10-30T00:00:00Z", false)));

		assertThat(decide(TITLE, candidates)).isEqualTo(existing(4, MatchKind.TITLE));
	}

	@Test
	void titleWindowEndsAtItsBoundary() {
		MatchCandidates candidates = new MatchCandidates(null, null, List.of(),
				List.of(title(4, "2026-10-30T00:00:01Z", false)));

		assertThat(decide(TITLE, candidates)).isEqualTo(new MatchDecision.New(false));
	}

	@Test
	void articlesSeenInThisFeedAreNotTitleCandidates() {
		MatchCandidates candidates = new MatchCandidates(null, null, List.of(),
				List.of(title(4, "2026-10-27T23:00:00Z", true)));

		assertThat(decide(TITLE, candidates)).isEqualTo(new MatchDecision.New(false));
	}

	@Test
	void closestTitleCandidateWins() {
		MatchCandidates candidates = new MatchCandidates(null, null, List.of(),
				List.of(title(4, "2026-10-26T01:00:00Z", false), title(6, "2026-10-28T02:00:00Z", false)));

		assertThat(decide(TITLE, candidates)).isEqualTo(existing(6, MatchKind.TITLE));
	}

	@Test
	void titleTieGoesToTheSmallestId() {
		MatchCandidates candidates = new MatchCandidates(null, null, List.of(),
				List.of(title(8, "2026-10-27T21:00:00Z", false), title(6, "2026-10-28T03:00:00Z", false)));

		assertThat(decide(TITLE, candidates)).isEqualTo(existing(6, MatchKind.TITLE));
	}

	@Test
	void emptyTitleKeyNeverMatchesByGuidOrTitle() {
		String separatorsOnly = "\u00A0\u2003";
		MatchCandidates candidates = new MatchCandidates(null, null,
				List.of(guid(5, TitleKey.of(separatorsOnly).hash())), List.of(title(4, "2026-10-28T00:00:00Z", false)));

		assertThat(decide(separatorsOnly, candidates)).isEqualTo(new MatchDecision.New(false));
	}

	@Test
	void titleWindowSpansFortyEightHoursEachWay() {
		assertThat(ArticleMatching.titleWindowAround(PUBLISHED_AT)).isEqualTo(
				new TimeRange(Instant.parse("2026-10-26T00:00:00Z"), Instant.parse("2026-10-30T00:00:00Z")));
	}

	private static MatchDecision decide(String title, MatchCandidates candidates) {
		FetchedArticle article = new FetchedArticle(title, URI.create("https://a.example/news/1"), "g-1", null,
				PUBLISHED_AT, false, null, List.of(), null);
		IncomingItem item = IncomingItem
			.from(new ArticleFetched(new SourceId(1), new FeedId(2), Instant.parse("2026-10-28T01:00:00Z"), article));
		return ArticleMatching.decide(item, candidates);
	}

	private static ArticleId id(long value) {
		return new ArticleId(value);
	}

	private static GuidCandidate guid(long articleId, KeyHash titleKeyHash) {
		return new GuidCandidate(new ArticleId(articleId), titleKeyHash);
	}

	private static TitleCandidate title(long articleId, String publishedAt, boolean seenInFeed) {
		return new TitleCandidate(new ArticleId(articleId), Instant.parse(publishedAt), seenInFeed);
	}

	private static MatchDecision existing(long articleId, MatchKind kind) {
		return new MatchDecision.Existing(new ArticleId(articleId), kind);
	}

}
