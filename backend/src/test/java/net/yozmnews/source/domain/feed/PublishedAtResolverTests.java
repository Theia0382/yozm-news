package net.yozmnews.source.domain.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import net.yozmnews.source.domain.profile.DateParsing;
import net.yozmnews.source.domain.profile.ProfileParse;
import net.yozmnews.source.domain.profile.ProfileParser;
import net.yozmnews.source.domain.profile.text.ProfileTextReader;
import org.junit.jupiter.api.Test;

/**
 * 발행 시각 해석 순서(RFC 822 → ISO 8601 → 프로필 패턴), 오프셋 없는 값의 시간대, 미래 시각. 날짜 설정은 운영자 프로필 검증을 거쳐
 * 만든다.
 */
class PublishedAtResolverTests {

	private static final Instant FETCHED_AT = Instant.parse("2026-10-25T00:00:00Z");

	private static final Instant LATER = Instant.parse("2026-10-30T00:00:00Z");

	@Test
	void readsRfc822() {
		assertThat(resolve("Sun, 25 Oct 2026 09:31:38 +0900", dates(""), LATER))
			.contains(Instant.parse("2026-10-25T00:31:38Z"));
	}

	@Test
	void readsIso8601WithAndWithoutAnOffset() {
		assertThat(resolve("2026-10-25T09:31:38+09:00", dates(""), LATER)).contains(Instant.parse("2026-10-25T00:31:38Z"));
		assertThat(resolve("2026-10-25T09:31:38Z", dates(""), LATER)).contains(Instant.parse("2026-10-25T09:31:38Z"));
		assertThat(resolve("2026-10-25T09:31:38", dates(""), LATER)).contains(Instant.parse("2026-10-25T00:31:38Z"));
		assertThat(resolve("2026-10-25T09:31:38", dates("  timeZone: Asia/Tokyo\n"), LATER))
			.contains(Instant.parse("2026-10-25T00:31:38Z"));
		assertThat(resolve("2026-10-25T09:31:38", dates("  timeZone: UTC\n"), LATER))
			.contains(Instant.parse("2026-10-25T09:31:38Z"));
	}

	@Test
	void readsAProfilePatternInTheProfileZone() {
		assertThat(resolve(" 2026-10-25 09:31:38 ", dates("  formats: [\"yyyy-MM-dd HH:mm:ss\"]\n"), LATER))
			.contains(Instant.parse("2026-10-25T00:31:38Z"));
		// 대조 — 패턴이 없으면 같은 원문을 읽지 못한다
		assertThat(resolve("2026-10-25 09:31:38", dates(""), LATER)).isEmpty();
	}

	@Test
	void readsAKoreanDate() {
		assertThat(resolve("2024년 10월 29일 오후 3:05", dates("  formats: [\"yyyy년 M월 d일 a h:mm\"]\n"), LATER))
			.contains(Instant.parse("2024-10-29T06:05:00Z"));
	}

	@Test
	void triesThePatternsInOrder() {
		DateParsing dates = dates("  formats: [\"yyyy.MM.dd HH:mm\", \"yyyy-MM-dd HH:mm:ss\"]\n");

		assertThat(resolve("2026.10.25 09:31", dates, LATER)).contains(Instant.parse("2026-10-25T00:31:00Z"));
		assertThat(resolve("2026-10-25 09:31:38", dates, LATER)).contains(Instant.parse("2026-10-25T00:31:38Z"));
	}

	@Test
	void triesIso8601BeforeTheProfilePatterns() {
		// 일 · 월 자리를 바꾼 패턴은 같은 원문을 11월 10일로 읽는다 — ISO 8601 이 먼저라 10월 11일이다
		DateParsing dates = dates("  formats: [\"yyyy-dd-MM'T'HH:mm:ss\"]\n");

		assertThat(resolve("2026-10-11T09:31:38", dates, Instant.parse("2026-12-01T00:00:00Z")))
			.contains(Instant.parse("2026-10-11T00:31:38Z"));
		// 대조 — ISO 8601 이 아닌 원문이면 일 · 월을 바꾼 패턴이 11월 10일로 읽는다(픽스처의 판별력)
		assertThat(resolve("2026-10-11 09:31:38", dates("  formats: [\"yyyy-dd-MM HH:mm:ss\"]\n"),
				Instant.parse("2026-12-01T00:00:00Z")))
			.contains(Instant.parse("2026-11-10T00:31:38Z"));
	}

	@Test
	void anOffsetInThePatternWinsOverTheProfileZone() {
		assertThat(resolve("2026-10-25 09:31:38 +01:00",
				dates("  formats: [\"yyyy-MM-dd HH:mm:ss XXX\"]\n  timeZone: Asia/Tokyo\n"), LATER))
			.contains(Instant.parse("2026-10-25T08:31:38Z"));
	}

	@Test
	void rejectsAValueTooFarInTheFuture() {
		DateParsing dates = dates("");

		assertThat(resolve("2026-10-25T01:00:00Z", dates, FETCHED_AT)).contains(Instant.parse("2026-10-25T01:00:00Z"));
		assertThat(resolve("2026-10-25T01:00:01Z", dates, FETCHED_AT)).isEmpty();
		// KST 시각을 GMT 로 적은 값 — 받은 시각보다 9시간 미래
		assertThat(resolve("Sun, 25 Oct 2026 09:00:00 GMT", dates, FETCHED_AT)).isEmpty();
	}

	@Test
	void givesNothingForBlankOrUnknownText() {
		assertThat(resolve("", dates(""), LATER)).isEmpty();
		assertThat(resolve("   ", dates(""), LATER)).isEmpty();
		assertThat(resolve("어제", dates(""), LATER)).isEmpty();
		assertThat(resolve("2026-10-25", dates(""), LATER)).isEmpty();
	}

	private static Optional<Instant> resolve(String text, DateParsing dates, Instant fetchedAt) {
		return PublishedAtResolver.resolve(text, dates, fetchedAt);
	}

	/** @param datesBody {@code dates:} 아래 들여쓴 줄 — 비면 기본값 */
	private static DateParsing dates(String datesBody) {
		String text = """
				source:
				  name: 알파일보
				  homepage: https://alpha.example
				  language: ko
				feeds:
				  - key: all
				    url: https://alpha.example/rss.xml
				    section: 전체
				""" + (datesBody.isEmpty() ? "" : "dates:\n" + datesBody);
		ProfileParse parse = new ProfileParser(new ProfileTextReader()).parseOperatorProfile(text);
		assertThat(parse).as("%s", parse).isInstanceOf(ProfileParse.Parsed.class);
		return ((ProfileParse.Parsed) parse).profile().dates();
	}

}
