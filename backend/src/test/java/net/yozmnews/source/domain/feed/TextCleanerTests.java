package net.yozmnews.source.domain.feed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 텍스트 정리 — HTML 제거 · 엔티티 해제 · 공백 정리, 요약문 상한.
 */
class TextCleanerTests {

	@ParameterizedTest
	@CsvSource(delimiter = '|', quoteCharacter = '\'', textBlock = """
			<p>요약&nbsp;문장</p><p>둘째</p>               | 요약 문장 둘째
			a<br>b                                         | a b
			x<script>alert(1)</script>y<style>p{}</style>z | x y z
			a < b &amp; c > d                              | a < b & c > d
			<!-- 주석 -->본문                              | 본문
			&copy 2026 &hellip;                            | © 2026 …
			<b>굵게</b>끝                                  | 굵게끝
			'  앞뒤   여러   공백  '                       | 앞뒤 여러 공백
			""")
	void extractsTheText(String raw, String expected) {
		assertThat(TextCleaner.text(raw)).contains(expected);
	}

	@Test
	void collapsesEveryKindOfSpace() {
		assertThat(TextCleaner.text("가　나\t다\n라\r\n마 바 사 아")).contains("가 나 다 라 마 바 사 아");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "　\n", "<p></p>", "<br/>", "<!-- 주석만 -->" })
	void givesNothingForEmptyText(String raw) {
		assertThat(TextCleaner.text(raw)).isEmpty();
		assertThat(TextCleaner.summary(raw)).isEmpty();
	}

	@Test
	void keepsASummaryAtTheLimit() {
		String atLimit = "가".repeat(1_000);

		assertThat(TextCleaner.summary(atLimit)).contains(atLimit);
		assertThat(TextCleaner.summary("<p>" + "나".repeat(999) + "</p>")).contains("나".repeat(999));
	}

	@Test
	void cutsTheSummaryAtTheLimit() {
		assertThat(TextCleaner.summary("가".repeat(1_001))).contains("가".repeat(1_000));
		// 본문급 요약문 — 태그를 지운 뒤 센다
		assertThat(TextCleaner.summary("<p>" + "다".repeat(1_500) + "</p>")).contains("다".repeat(1_000));
	}

	@Test
	void doesNotSplitASurrogatePair() {
		String text = "가".repeat(999) + "😀" + "나";

		assertThat(TextCleaner.summary(text)).contains("가".repeat(999) + "😀");
	}

	@Test
	void stripsTheSpaceLeftAtTheCut() {
		assertThat(TextCleaner.summary("가".repeat(999) + " 나")).contains("가".repeat(999));
	}

	@Test
	void leavesTheTitleUncut() {
		String long1500 = "라".repeat(1_500);

		assertThat(TextCleaner.text(long1500)).contains(long1500);
	}

}
