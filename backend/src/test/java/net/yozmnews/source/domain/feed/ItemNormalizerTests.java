package net.yozmnews.source.domain.feed;

import static net.yozmnews.source.domain.feed.FeedFixtures.normalized;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;

import net.yozmnews.source.FetchedArticle;
import net.yozmnews.source.SkipReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 항목 정규화 — 합성 픽스처의 항목으로 본다.
 * <p>
 * 지키는 규칙: source-R39 · source-R40 · source-R41
 */
class ItemNormalizerTests {

	private static final String CREATOR = """
			items:
			  author: ["dc:creator"]
			""";

	@ParameterizedTest
	@ValueSource(strings = { "pairing-rss20", "pairing-rss10", "pairing-atom10" })
	void pairsEachRomeEntryWithItsElement(String name) {
		assertThat(normalized(name, CREATOR).articles()).extracting(FetchedArticle::author).containsExactly("가", "나", "다");
	}

	@Test
	void keepsThePairingPastASkippedItem() {
		FeedNormalization.Normalized normalized = normalized("pairing-rss20-missing-link", CREATOR);

		assertThat(normalized.skipped()).containsExactly(new SkippedItem(2, SkipReason.MISSING_LINK));
		assertThat(normalized.articles()).extracting(FetchedArticle::author).containsExactly("가", "다");
	}

	@Test
	void skipsItemsWithoutAUsableLinkOrTitle() {
		FeedNormalization.Normalized normalized = normalized("skipped-items", "");

		assertThat(normalized.skipped()).containsExactly(new SkippedItem(1, SkipReason.MISSING_LINK),
				new SkippedItem(2, SkipReason.MISSING_LINK), new SkippedItem(3, SkipReason.INVALID_LINK),
				new SkippedItem(4, SkipReason.INVALID_LINK), new SkippedItem(5, SkipReason.INVALID_LINK),
				new SkippedItem(6, SkipReason.MISSING_TITLE));
		// 대조 — 링크와 제목이 있는 항목은 기사다
		assertThat(normalized.articles()).extracting(FetchedArticle::title).containsExactly("정상 항목");
	}

	@Test
	void readsMetadataFromTheProfileSourcesInOrder() {
		List<FetchedArticle> articles = normalized("metadata", """
				items:
				  thumbnail: ["media:thumbnail", $standard]
				  category: ["dc:subject", $standard]
				""").articles();

		assertThat(articles).extracting(FetchedArticle::thumbnail)
			.containsExactly(URI.create("https://alpha.example/e1.jpg"), URI.create("https://alpha.example/m2.jpg"), null,
					URI.create("https://alpha.example/m4.jpg"));
		assertThat(articles).extracting(FetchedArticle::categories)
			.containsExactly(List.of("정치", "국회"), List.of("경제"), List.of("사회"), List.of());
		assertThat(articles).extracting(FetchedArticle::guid).containsExactly("g-1", null, null, null);
	}

	@Test
	void standardMetadataIgnoresTheMappedElements() {
		// 대조 — 기본 프로필은 media:thumbnail · dc:subject 를 읽지 않는다
		List<FetchedArticle> articles = normalized("metadata", "").articles();

		assertThat(articles).extracting(FetchedArticle::thumbnail)
			.containsExactly(URI.create("https://alpha.example/e1.jpg"), null, null, null);
		assertThat(articles).extracting(FetchedArticle::categories)
			.containsExactly(List.of("정치", "국회"), List.of("정치"), List.of("사회"), List.of());
	}

	@Test
	void anEmptySourceListReadsNothing() {
		FetchedArticle article = normalized("rss20", """
				items:
				  summary: []
				  publishedAt: []
				""").articles().get(0);

		assertThat(article.summary()).isNull();
		assertThat(article.publishedAtEstimated()).isTrue();
	}

}
