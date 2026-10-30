package net.yozmnews.source.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import net.yozmnews.source.FetchedArticle;
import net.yozmnews.source.domain.feed.FeedFixtures;
import net.yozmnews.source.domain.feed.FeedNormalization;
import net.yozmnews.source.domain.feed.SkipReason;
import net.yozmnews.source.domain.feed.SkippedItem;
import net.yozmnews.source.domain.fetch.FetchResult;
import net.yozmnews.source.domain.fetch.FetchState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * 미리보기의 지표 — 손으로 만든 정규화 결과에서 센다. 기대값은 기사 목록을 사람이 센 리터럴이다.
 * <p>
 * 지키는 규칙: source-R9
 */
class FeedPreviewTests {

	private static final Instant FETCHED_AT = FeedFixtures.FETCHED_AT;

	private static final Instant EARLY = Instant.parse("2026-10-25T00:00:00Z");

	private static final Instant LATE = Instant.parse("2026-10-25T03:00:00Z");

	private static final URI MOVED = URI.create("https://alpha.example/moved.xml");

	@Test
	void countsTheMetricsOfAFetchedFeed() {
		List<FetchedArticle> articles = List.of(article("하나", "abc", EARLY, false),
				article("둘", "abcde", LATE, false), article("셋", null, FETCHED_AT, true));
		FeedNormalization.Normalized normalized = new FeedNormalization.Normalized(articles,
				List.of(new SkippedItem(4, SkipReason.MISSING_LINK)), 2, 1_000);

		FeedPreview preview = FeedPreview.of(new FetchResult.Fetched(normalized, FetchState.NONE, FETCHED_AT, MOVED),
				FeedFixtures.profile(""));

		assertThat(preview).isEqualTo(new FeedPreview(3, List.of(new SkippedItem(4, SkipReason.MISSING_LINK)), 2,
				false, new SummaryLengths(2, 1, 3, 3, 5), 2, 1_000, LATE, MOVED,
				List.of(new ArticleSample("하나", EARLY, false), new ArticleSample("둘", LATE, false),
						new ArticleSample("셋", FETCHED_AT, true))));
		assertThat(preview.items()).isEqualTo(4);
	}

	@Test
	void takesTheLowerMedianAndTheFirstFiveSamples() {
		List<FetchedArticle> articles = new ArrayList<>();
		String[] summaries = { "abcdefghi", "ab", "abcd", null, "가\uD83D\uDE00다", "abcdefgh", "a" };
		for (int i = 0; i < summaries.length; i++) {
			articles.add(article("기사" + i, summaries[i], EARLY, false));
		}

		FeedPreview preview = preview(articles, "");

		// 코드 포인트 길이 1 · 2 · 3 · 4 · 8 · 9(가 · U+1F600 · 다는 UTF-16 단위로 4) — 여섯의 아래 중앙값은 0 부터 세어 (6 - 1) / 2 = 2 번째인 3
		assertThat(preview.summaries()).isEqualTo(new SummaryLengths(6, 1, 1, 3, 9));
		assertThat(preview.samples()).extracting(ArticleSample::title)
			.containsExactly("기사0", "기사1", "기사2", "기사3", "기사4");
	}

	@Test
	void takesTheMiddleOfAnOddCount() {
		FeedPreview preview = preview(List.of(article("가", "abcdefghi", EARLY, false),
				article("나", "ab", EARLY, false), article("다", "abcd", EARLY, false)), "");

		assertThat(preview.summaries()).isEqualTo(new SummaryLengths(3, 0, 2, 4, 9));
	}

	@Test
	void hasNoDistributionOrLatestTimeWithoutSummariesOrParsedDates() {
		FeedPreview preview = preview(List.of(article("가", null, FETCHED_AT, true)), "");

		assertThat(preview.summaries()).isEqualTo(new SummaryLengths(0, 1, null, null, null));
		assertThat(preview.latestPublishedAt()).isNull();
		assertThat(preview.datedArticles()).isZero();
	}

	@Test
	void marksAProfileThatDeclaresNoPublishedAt() {
		List<FetchedArticle> articles = List.of(article("가", null, FETCHED_AT, true));

		assertThat(preview(articles, """
				items:
				  publishedAt: []
				""").publishedAtUnread()).isTrue();
		// 대조 — 적지 않으면 [$standard] 다
		assertThat(preview(articles, "").publishedAtUnread()).isFalse();
		// 대조 — 다른 값을 비워도 발행 시각은 읽는다
		assertThat(preview(articles, """
				items:
				  summary: []
				""").publishedAtUnread()).isFalse();
	}

	private static FeedPreview preview(List<FetchedArticle> articles, String profile) {
		return FeedPreview.of(new FetchResult.Fetched(new FeedNormalization.Normalized(articles, List.of(), 0, 1_000),
				FetchState.NONE, FETCHED_AT, null), FeedFixtures.profile(profile));
	}

	private static FetchedArticle article(String title, @Nullable String summary, Instant publishedAt,
			boolean estimated) {
		return new FetchedArticle(title, URI.create("https://alpha.example/news/" + title.hashCode()), null, summary,
				publishedAt, estimated, null, List.of(), null);
	}

}
