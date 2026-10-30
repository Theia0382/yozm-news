package net.yozmnews.source.domain.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 요청할 수 있는 주소와 리다이렉트 대상 해석.
 * <p>
 * 지키는 규칙: source-R35
 */
class FetchUrlsTests {

	private static final URI CURRENT = URI.create("http://a.example/x/y?q=1");

	@ParameterizedTest
	@CsvSource({ "https://b.example/feed, https://b.example/feed", "/a, http://a.example/a", "b, http://a.example/x/b",
			"../c, http://a.example/c", "//h.example/x, http://h.example/x", "/a#top, http://a.example/a",
			"'  /a  ', http://a.example/a", "?p=2, http://a.example/x/y?p=2" })
	void resolvesTheLocationAgainstTheCurrentUrl(String location, String expected) {
		assertThat(FetchUrls.resolveLocation(CURRENT, location)).contains(URI.create(expected));
	}

	/** RFC 3986 §5.2.4 — 루트를 넘는 {@code ..} 와 절대 경로 안의 점 조각도 지운다 */
	@ParameterizedTest
	@CsvSource({ "../../c, http://a.example/c", "../../../c, http://a.example/c", "/./a, http://a.example/a",
			"/../a, http://a.example/a", "/a/./b/../c, http://a.example/a/c",
			"https://b.example/x/../feed, https://b.example/feed", "?p=../2, http://a.example/x/y?p=../2" })
	void removesDotSegments(String location, String expected) {
		assertThat(FetchUrls.resolveLocation(CURRENT, location)).contains(URI.create(expected));
	}

	@Test
	void resolvesAgainstAUrlWithoutPath() {
		assertThat(FetchUrls.resolveLocation(URI.create("http://h.example"), "b")).contains(URI.create("http://h.example/b"));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "   ", "http://[x", "/a b" })
	void emptyWhenTheLocationIsMissingOrUnreadable(@Nullable String location) {
		assertThat(FetchUrls.resolveLocation(CURRENT, location)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://a.example/1", "HTTP://A/1", "https://a.example:8443/feed?x=1",
			"http://a.example:1/", "http://a.example:65535/" })
	void fetchable(String url) {
		assertThat(FetchUrls.isFetchable(URI.create(url))).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "ftp://a/", "https:foo", "file:///etc/passwd", "http://user:pw@a/", "/relative",
			"mailto:a@a.example", "http://a.example:0/", "http://a.example:65536/", "http://a.example:99999/" })
	void notFetchable(String url) {
		assertThat(FetchUrls.isFetchable(URI.create(url))).isFalse();
	}

}
