package net.yozmnews.article.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 정규 URL — 기대값은 손으로 적은 리터럴이다.
 * <p>
 * 지키는 규칙: article-R1
 */
class CanonicalUrlTests {

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			HTTPS://Example.COM:443/a/B?utm_source=rss&id=7#top | https://example.com/a/B?id=7
			http://a.example                                    | http://a.example/
			http://a.example:80/x                               | http://a.example/x
			http://a.example:8080/x                             | http://a.example:8080/x
			https://a.example:80/x                              | https://a.example:80/x
			https://a.example/x?fbclid=abc                      | https://a.example/x
			https://a.example/x?UTM_Medium=a&b=%2F&c            | https://a.example/x?b=%2F&c
			https://a.example/x?b=2&a=1                         | https://a.example/x?b=2&a=1
			https://a.example/x?                                | https://a.example/x
			https://a.example/x?id=7&                           | https://a.example/x?id=7&
			https://a.example/%EA%B0%80?q=%ED%95%9C             | https://a.example/%EA%B0%80?q=%ED%95%9C
			https://[2001:db8::1]:443/x                         | https://[2001:db8::1]/x
			https://u:p%40@A.example/x                          | https://u:p%40@a.example/x
			""")
	void canonicalizes(String link, String expected) {
		assertThat(CanonicalUrl.of(URI.create(link)).value()).isEqualTo(expected);
	}

	@Test
	void hashesTheCanonicalValue() {
		assertThat(CanonicalUrl.of(URI.create("HTTPS://Example.COM/a/B?id=7&utm_campaign=x")).hash())
			.hasToString("4bf04c4dc63907accf6c03a1cbda25cbceecf0f0de3be79491cdfb8bc1497521");
	}

	@ParameterizedTest
	@ValueSource(strings = { "/news/1", "ftp://a.example/1", "mailto:a@a.example" })
	void rejectsALinkThatIsNotAWebUrl(String link) {
		assertThatIllegalArgumentException().isThrownBy(() -> CanonicalUrl.of(URI.create(link)));
	}

}
