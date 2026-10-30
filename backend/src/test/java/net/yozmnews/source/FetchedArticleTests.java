package net.yozmnews.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 공개 타입의 생성 제약(docs/specs/source.md §3) — 정규화를 거치지 않고 값을 직접 만들어 본다
 */
class FetchedArticleTests {

	private static final URI LINK = URI.create("https://a.example/news/1");

	private static final Instant PUBLISHED_AT = Instant.parse("2026-10-25T00:31:38Z");

	@Test
	void keepsEveryValue() {
		List<String> categories = new ArrayList<>(List.of("정치", "국회"));
		FetchedArticle article = new FetchedArticle("제목", LINK, "g-1", "요약", PUBLISHED_AT, true, "홍길동", categories,
				URI.create("http://a.example/t.jpg"));
		categories.add("바뀜");

		assertThat(article.title()).isEqualTo("제목");
		assertThat(article.link()).isEqualTo(LINK);
		assertThat(article.guid()).isEqualTo("g-1");
		assertThat(article.summary()).isEqualTo("요약");
		assertThat(article.publishedAt()).isEqualTo(PUBLISHED_AT);
		assertThat(article.publishedAtEstimated()).isTrue();
		assertThat(article.author()).isEqualTo("홍길동");
		assertThat(article.categories()).containsExactly("정치", "국회");
		assertThat(article.thumbnail()).isEqualTo(URI.create("http://a.example/t.jpg"));
	}

	@Test
	void allowsMissingOptionalValues() {
		FetchedArticle article = new FetchedArticle("제목", LINK, null, null, PUBLISHED_AT, false, null, List.of(), null);

		assertThat(article.guid()).isNull();
		assertThat(article.categories()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void rejectsABlankTitle(String title) {
		assertThatIllegalArgumentException().isThrownBy(() -> article(title, LINK, null, null));
	}

	@ParameterizedTest
	@ValueSource(strings = { "/news/1", "ftp://a.example/1", "https:foo", "mailto:a@a.example", "javascript:alert(1)" })
	void rejectsALinkThatIsNotAWebUrl(String link) {
		assertThatIllegalArgumentException().isThrownBy(() -> article("제목", URI.create(link), null, null));
	}

	@Test
	void rejectsAThumbnailThatIsNotAWebUrl() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> article("제목", LINK, null, URI.create("/t.jpg")));
	}

	@Test
	void acceptsWebUrlsWhateverTheSchemeCase() {
		assertThat(FetchedArticle.isWebUrl(URI.create("HTTPS://A.EXAMPLE/1"))).isTrue();
		assertThat(FetchedArticle.isWebUrl(URI.create("http://a.example"))).isTrue();
		assertThat(FetchedArticle.isWebUrl(URI.create("https:foo"))).isFalse();
		assertThat(FetchedArticle.isWebUrl(URI.create("https:///no-host"))).isFalse();
	}

	@Test
	void capsTheSummaryAt1000CodePoints() {
		// 보충 문자(서로게이트 쌍)도 한 코드 포인트다
		String atLimit = "가".repeat(999) + "😀";
		assertThat(article("제목", LINK, atLimit, null).summary()).isEqualTo(atLimit);
		assertThatIllegalArgumentException().isThrownBy(() -> article("제목", LINK, atLimit + "가", null));
	}

	@Test
	void rejectsIdentifiersBelowOne() {
		assertThat(new SourceId(1).value()).isEqualTo(1);
		assertThat(new FeedId(1).value()).isEqualTo(1);
		assertThatIllegalArgumentException().isThrownBy(() -> new SourceId(0));
		assertThatIllegalArgumentException().isThrownBy(() -> new FeedId(0));
		assertThatIllegalArgumentException().isThrownBy(() -> new SourceId(-1));
	}

	private static FetchedArticle article(String title, URI link, @Nullable String summary, @Nullable URI thumbnail) {
		return new FetchedArticle(title, link, null, summary, PUBLISHED_AT, false, null, List.of(), thumbnail);
	}

}
