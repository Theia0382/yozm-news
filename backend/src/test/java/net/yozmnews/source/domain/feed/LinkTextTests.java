package net.yozmnews.source.domain.feed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 링크 원문의 퍼센트 인코딩 — 대상 문자 집합.
 * <p>
 * 지키는 규칙: source-R40
 */
class LinkTextTests {

	@ParameterizedTest
	@CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
			"'a b'   | a%20b",
			"'a\"b'  | a%22b",
			"'a<b'   | a%3Cb",
			"'a>b'   | a%3Eb",
			"'a\\b'  | a%5Cb",
			"'a^b'   | a%5Eb",
			"'a`b'   | a%60b",
			"'a{b'   | a%7Bb",
			"'a|b'   | a%7Cb",
			"'a}b'   | a%7Db" })
	void encodesEachUnsafeCharacter(String text, String expected) {
		assertThat(LinkText.encodeUnsafe(text)).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://alpha.example/뉴스/1", "https://alpha.example/a%20b", "https://alpha.example/a?q=1#top",
			"https://alpha.example/[1]", "",
			// RFC 3986 이 허용하는 ASCII 인쇄 문자 85개 전부
			"!#$%&'()*+,-./0123456789:;=?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]_abcdefghijklmnopqrstuvwxyz~" })
	void leavesOtherTextAsItIs(String text) {
		assertThat(LinkText.encodeUnsafe(text)).isEqualTo(text);
	}

	@Test
	void encodesEveryOccurrence() {
		assertThat(LinkText.encodeUnsafe("https://alpha.example/K-TECH Global Leaders/a b"))
			.isEqualTo("https://alpha.example/K-TECH%20Global%20Leaders/a%20b");
	}

}
