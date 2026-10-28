package net.yozmnews.source.domain.feed;

import static net.yozmnews.source.domain.feed.FeedFixtures.FETCHED_AT;
import static net.yozmnews.source.domain.feed.FeedFixtures.normalized;
import static net.yozmnews.source.domain.feed.FeedFixtures.unreadable;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import net.yozmnews.source.FetchedArticle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * RSS 0.9x · 1.0 · 2.0 · Atom 을 같은 기사 구조로 읽는다(AC1). 기본 프로필(모든 항목 {@code $standard})로 읽는다.
 */
class FeedNormalizerFormatTests {

	private static final Instant KST_0931 = Instant.parse("2026-10-25T00:31:38Z");

	@Test
	void readsRss20() {
		assertThat(normalized("rss20", "").articles()).containsExactly(
				new FetchedArticle("국회, 예산안 처리", URI.create("https://alpha.example/news/1"), "alpha-1", "여야가 합의했다.",
						KST_0931, false, "reporter@alpha.example (홍길동)", List.of("정치", "국회"),
						URI.create("https://alpha.example/t1.jpg")),
				new FetchedArticle("두 번째", URI.create("https://alpha.example/news/2"), null, null, FETCHED_AT, true, null,
						List.of(), null));
	}

	@Test
	void readsRss091WithItsPublicDoctype() {
		assertThat(normalized("rss091", "").articles()).containsExactly(new FetchedArticle("공개 DTD 의 RSS 0.91",
				URI.create("https://alpha.example/news/91"), null, "0.91 요약", FETCHED_AT, true, null, List.of(), null));
	}

	@Test
	void readsRss092() {
		assertThat(normalized("rss092", "").articles()).containsExactly(new FetchedArticle("RSS 0.92 항목",
				URI.create("https://alpha.example/news/92"), null, "0.92 요약", FETCHED_AT, true, null, List.of("경제"),
				URI.create("https://alpha.example/t92.png")));
	}

	@Test
	void readsRss10() {
		assertThat(normalized("rss10", "").articles()).containsExactly(new FetchedArticle("RSS 1.0 항목",
				URI.create("https://alpha.example/news/10"), "https://alpha.example/news/10", "1.0 요약", KST_0931, false,
				"김기자", List.of("사회"), null));
	}

	@Test
	void readsAtom10() {
		assertThat(normalized("atom10", "").articles()).containsExactly(
				new FetchedArticle("경제 <속보>", URI.create("https://alpha.example/news/a1"), "urn:alpha:1", "코스피가 올랐다.",
						KST_0931, false, "이기자", List.of("경제", "증시"), URI.create("https://alpha.example/a1.jpg")),
				// updated 는 발행 시각이 아니다
				new FetchedArticle("발행 시각 없는 항목", URI.create("https://alpha.example/news/a2"), "urn:alpha:2", null,
						FETCHED_AT, true, null, List.of(), null));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			rss090            | RSS_090
			rss091            | RSS_091N
			rss091-nodoctype  | RSS_091U
			rss092            | RSS_092
			rss093            | RSS_093
			rss094            | RSS_094
			rss20             | RSS_20
			rss20-userland-ns | RSS_20
			rss10             | RSS_10
			atom03            | ATOM_03
			atom10            | ATOM_10
			""")
	void detectsTheFormat(String name, FeedFormat expected) {
		FeedXmlReader.Read read = FeedXmlReader.read(new String(FeedFixtures.bytes(name), StandardCharsets.UTF_8));
		assertThat(read).isInstanceOf(FeedXmlReader.Read.Parsed.class);

		RomeFeed.Built built = RomeFeed.build(((FeedXmlReader.Read.Parsed) read).document());

		assertThat(built).isInstanceOf(RomeFeed.Built.Feed.class);
		assertThat(((RomeFeed.Built.Feed) built).format()).isEqualTo(expected);
	}

	@Test
	void readsTheOtherRssVersions() {
		assertThat(normalized("rss090", "").articles()).containsExactly(new FetchedArticle("RSS 0.90 항목",
				URI.create("https://alpha.example/news/90"), null, null, FETCHED_AT, true, null, List.of(), null));
		assertThat(normalized("rss091-nodoctype", "").articles()).containsExactly(new FetchedArticle("DOCTYPE 없는 0.91",
				URI.create("https://alpha.example/news/911"), null, null, FETCHED_AT, true, null, List.of(), null));
		assertThat(normalized("rss093", "").articles()).containsExactly(new FetchedArticle("RSS 0.93 항목",
				URI.create("https://alpha.example/news/93"), null, null, KST_0931, false, null, List.of(), null));
		assertThat(normalized("rss094", "").articles()).containsExactly(new FetchedArticle("RSS 0.94 항목",
				URI.create("https://alpha.example/news/94"), null, null, KST_0931, false, null, List.of(), null));
	}

	@Test
	void readsAtom03Issued() {
		assertThat(normalized("atom03", "").articles()).containsExactly(new FetchedArticle("Atom 0.3 항목",
				URI.create("https://alpha.example/news/03"), "urn:alpha:03", null, KST_0931, false, null, List.of(), null));
	}

	@Test
	void readsRss20InTheUserlandNamespace() {
		List<FetchedArticle> articles = normalized("rss20-userland-ns", "").articles();

		assertThat(articles).containsExactly(new FetchedArticle("기본 이름공간 RSS 2.0",
				URI.create("https://alpha.example/news/u1"), "u-1", null, KST_0931, false, null, List.of(), null));
		// 대조 — 루트의 이름공간 선언만 뺀 쌍둥이 문서와 같다
		assertThat(normalized("rss20-userland-ns-twin", "").articles()).isEqualTo(articles);
	}

	@Test
	void rejectsAnRssRootInAnotherNamespace() {
		assertThat(unreadable("rss-in-atom-namespace", "").problem())
			.isEqualTo(new DocumentProblem(DocumentProblemKind.NOT_A_FEED, "RSS · Atom 피드가 아니다", null, null));
	}

	@Test
	void rejectsXmlThatIsNotAFeed() {
		FeedNormalization result = FeedFixtures.normalize("<html><body>피드 아님</body></html>".getBytes(StandardCharsets.UTF_8),
				"");

		assertThat(result).isEqualTo(new FeedNormalization.Unreadable(
				new DocumentProblem(DocumentProblemKind.NOT_A_FEED, "RSS · Atom 피드가 아니다", null, null)));
	}

}
