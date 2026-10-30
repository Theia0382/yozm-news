package net.yozmnews.source.domain.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * RFC 822 날짜의 해석 범위 — 받는 변형과 버리는 변형을 표로 본다.
 * <p>
 * 지키는 규칙: source-R40
 */
class Rfc822DateTimeTests {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			Sun, 25 Oct 2026 09:31:38 +0900   | 2026-10-25T00:31:38Z
			25 Oct 2026 09:31:38 +0900        | 2026-10-25T00:31:38Z
			Sun, 25 Oct 2026 09:31 +0900      | 2026-10-25T00:31:00Z
			Sun, 25 Oct 2026 09:31:38 GMT     | 2026-10-25T09:31:38Z
			Sun, 25 Oct 2026 09:31:38 UT      | 2026-10-25T09:31:38Z
			Sun, 25 Oct 2026 09:31:38 EST     | 2026-10-25T14:31:38Z
			Sun, 25 Oct 2026 09:31:38 PDT     | 2026-10-25T16:31:38Z
			Sun, 25 Oct 2026 09:31:38 -0430   | 2026-10-25T14:01:38Z
			sun, 25 oct 2026 09:31:38 +0900   | 2026-10-25T00:31:38Z
			Mon, 25 Oct 2026 09:31:38 +0900   | 2026-10-25T00:31:38Z
			Mon, 5 Oct 2026 09:31:38 +0900    | 2026-10-05T00:31:38Z
			Sun,25 Oct 2026 09:31:38 +0900    | 2026-10-25T00:31:38Z
			Sun, 25 Oct 2026 09:31:38         | 2026-10-25T00:31:38Z
			""")
	void readsTheVariants(String text, String expected) {
		assertThat(Rfc822DateTime.parse(text, SEOUL)).contains(Instant.parse(expected));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			Asia/Tokyo    | 2026-10-25T00:31:38Z
			UTC           | 2026-10-25T09:31:38Z
			""")
	void usesTheProfileZoneWithoutAZone(String zone, String expected) {
		assertThat(Rfc822DateTime.parse("Sun, 25 Oct 2026 09:31:38", ZoneId.of(zone))).contains(Instant.parse(expected));
	}

	@ParameterizedTest
	@ValueSource(strings = { "Sun, 25 Oct 26 09:31:38 +0900", "Sun, 25 Oct 2026 09:31:38 KST",
			"Sun, 31 Feb 2026 09:31:38 +0900", "Sun, 25 Oct 2026 24:00:00 +0900", "Sun, 25 Oct 2026 09:31:38 +0960",
			"Sun, 25 Oct 2026 09:31:60 +0900", "Xyz, 25 Oct 2026 09:31:38 +0900", "Sun, 25 Okt 2026 09:31:38 +0900",
			"2026-10-25 09:31:38", "2026-10-25T09:31:38+09:00", "" })
	void rejectsOtherForms(String text) {
		assertThat(Rfc822DateTime.parse(text, SEOUL)).isEmpty();
	}

}
