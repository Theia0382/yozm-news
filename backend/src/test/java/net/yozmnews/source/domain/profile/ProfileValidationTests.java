package net.yozmnews.source.domain.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import net.yozmnews.source.domain.profile.text.ProfileTextReader;
import net.yozmnews.source.domain.profile.text.TextLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 운영자 프로필(프리셋 없음)의 검증 — 모델 값, 기본값, 그리고 문제마다 경로와 종류.
 */
class ProfileValidationTests {

	private static final String SOURCE = """
			source:
			  name: 블로터
			  homepage: https://www.bloter.net
			  language: ko
			""";

	private static final String FEED = """
			feeds:
			  - key: all
			    url: https://www.bloter.net/rss/allArticle.xml
			    section: 전체
			""";

	private final ProfileParser parser = new ProfileParser(new ProfileTextReader());

	@Test
	void buildsEveryValueOfAFullProfile() {
		CollectionProfile profile = parsed("""
				source:
				  name: 블로터
				  homepage: https://www.bloter.net
				  language: ko
				termsReviewedAt: 2026-10-25
				feeds:
				  - key: all
				    url: https://www.bloter.net/rss/allArticle.xml
				    section: 전체
				  - key: breaking
				    url: https://www.bloter.net/rss/breaking.xml
				    section: 속보
				    pollIntervalMinutes: 15
				pollIntervalMinutes: 30
				preCorrections:
				  - rule: replace-undefined-entities
				  - rule: strip-invalid-control-chars
				  - rule: force-encoding
				    charset: EUC-KR
				items:
				  publishedAt: [$standard, "dc:date"]
				  summary: [$standard, "content:encoded"]
				  author: ["ex:writer", byline]
				  thumbnail: []
				namespaces:
				  ex: http://example.com/ns/
				dates:
				  formats: ["yyyy-MM-dd HH:mm:ss", "yyyy년 M월 d일 a h:mm"]
				  timeZone: Asia/Tokyo
				""");

		assertThat(profile.source()).isEqualTo(new SourceInfo("블로터", URI.create("https://www.bloter.net"), Locale.KOREAN));
		assertThat(profile.termsReviewedAt()).contains(LocalDate.of(2026, 10, 25));
		assertThat(profile.feeds()).containsExactly(
				new FeedSpec(new FeedKey("all"), URI.create("https://www.bloter.net/rss/allArticle.xml"), "전체", null),
				new FeedSpec(new FeedKey("breaking"), URI.create("https://www.bloter.net/rss/breaking.xml"), "속보",
						new PollInterval(Duration.ofMinutes(15))));
		assertThat(profile.pollInterval()).contains(new PollInterval(Duration.ofMinutes(30)));
		assertThat(profile.preCorrections()).containsExactly(new PreCorrection.ReplaceUndefinedEntities(),
				new PreCorrection.StripInvalidControlChars(), new PreCorrection.ForceEncoding(Charset.forName("EUC-KR")));
		assertThat(profile.items().get(ItemField.PUBLISHED_AT)).containsExactly(new ValueSource.Standard(),
				element("http://purl.org/dc/elements/1.1/", "date"));
		assertThat(profile.items().get(ItemField.SUMMARY)).containsExactly(new ValueSource.Standard(),
				element("http://purl.org/rss/1.0/modules/content/", "encoded"));
		assertThat(profile.items().get(ItemField.AUTHOR)).containsExactly(element("http://example.com/ns/", "writer"),
				element("", "byline"));
		assertThat(profile.items().get(ItemField.CATEGORY)).containsExactly(new ValueSource.Standard());
		assertThat(profile.items().get(ItemField.THUMBNAIL)).isEmpty();
		assertThat(profile.dates().patterns()).extracting(DatePattern::pattern)
			.containsExactly("yyyy-MM-dd HH:mm:ss", "yyyy년 M월 d일 a h:mm");
		assertThat(profile.dates().patterns().get(1).formatter().getLocale()).isEqualTo(Locale.KOREAN);
		assertThat(profile.dates().zone()).isEqualTo(ZoneId.of("Asia/Tokyo"));
		assertThat(profile.feed(new FeedKey("breaking"))).contains(profile.feeds().get(1));
		assertThat(profile.feed(new FeedKey("none"))).isEmpty();
	}

	@Test
	void fillsDefaultsForOptionalItems() {
		CollectionProfile profile = parsed(SOURCE + FEED);

		assertThat(profile.termsReviewedAt()).isEmpty();
		assertThat(profile.pollInterval()).isEmpty();
		assertThat(profile.feeds().get(0).pollInterval()).isNull();
		assertThat(profile.preCorrections()).isEmpty();
		assertThat(profile.items()).containsOnlyKeys(ItemField.values());
		assertThat(profile.items().values()).allSatisfy(sources -> assertThat(sources)
			.containsExactly(new ValueSource.Standard()));
		assertThat(profile.dates().patterns()).isEmpty();
		assertThat(profile.dates().zone()).isEqualTo(ZoneId.of("Asia/Seoul"));
	}

	@Test
	void acceptsAnOperatorProfileWithATermsReviewDate() {
		assertThat(parsed(SOURCE + FEED + "termsReviewedAt: 2026-10-25\n").termsReviewedAt())
			.contains(LocalDate.of(2026, 10, 25));
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "2026-13-01", "2026-02-30", "26-10-25", "2026/10/25" })
	void rejectsATermsReviewDateThatIsNotAnIsoDate(String date) {
		assertThat(problems(SOURCE + FEED + "termsReviewedAt: " + date + "\n"))
			.containsExactly("termsReviewedAt INVALID_TERMS_REVIEWED_AT");
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			name     | source.name
			homepage | source.homepage
			language | source.language
			""")
	void rejectsAMissingSourceItem(String item, String path) {
		String source = SOURCE.lines().filter(line -> !line.trim().startsWith(item + ":")).map(line -> line + "\n")
			.reduce("", String::concat);

		assertThat(problems(source + FEED)).containsExactly(path + " MISSING_REQUIRED");
	}

	@Test
	void rejectsMissingFeedsAndFeedItems() {
		assertThat(problems(SOURCE)).containsExactly("feeds MISSING_REQUIRED");
		assertThat(problems(SOURCE + "feeds: []\n")).containsExactly("feeds NO_FEEDS");
		assertThat(problems(SOURCE + """
				feeds:
				  - url: https://a.example/rss
				    section: 전체
				  - key: b
				    section: 전체
				  - key: c
				    url: https://c.example/rss
				""")).containsExactly("feeds[0].key MISSING_REQUIRED", "feeds[1].url MISSING_REQUIRED",
				"feeds[2].section MISSING_REQUIRED");
	}

	@Test
	void rejectsAnEmptyText() {
		assertThat(problems("")).containsExactly("source MISSING_REQUIRED", "feeds MISSING_REQUIRED");
	}

	@Test
	void rejectsBlankNames() {
		assertThat(problems("""
				source:
				  name: "  "
				  homepage: https://www.bloter.net
				  language: ko
				feeds:
				  - key: all
				    url: https://www.bloter.net/rss/allArticle.xml
				    section: ""
				""")).containsExactly("source.name BLANK", "feeds[0].section BLANK");
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "run-script", "Replace-Undefined-Entities", "exec" })
	void rejectsAnUnknownPreCorrection(String rule) {
		assertThat(problems(SOURCE + FEED + "preCorrections:\n  - rule: " + rule + "\n"))
			.containsExactly("preCorrections[0].rule UNKNOWN_PRE_CORRECTION");
	}

	@Test
	void rejectsTheSamePreCorrectionTwice() {
		assertThat(problems(SOURCE + FEED + """
				preCorrections:
				  - rule: force-encoding
				    charset: EUC-KR
				  - rule: force-encoding
				    charset: UTF-8
				""")).containsExactly("preCorrections[1].rule DUPLICATE_PRE_CORRECTION");
		assertThat(problems(SOURCE + FEED + """
				preCorrections:
				  - rule: replace-undefined-entities
				  - rule: strip-invalid-control-chars
				  - rule: replace-undefined-entities
				""")).containsExactly("preCorrections[2].rule DUPLICATE_PRE_CORRECTION");
	}

	@Test
	void acceptsEveryPreCorrectionOnce() {
		assertThat(parsed(SOURCE + FEED + """
				preCorrections:
				  - rule: strip-invalid-control-chars
				  - rule: force-encoding
				    charset: UTF-8
				  - rule: replace-undefined-entities
				""").preCorrections()).containsExactly(new PreCorrection.StripInvalidControlChars(),
						new PreCorrection.ForceEncoding(Charset.forName("UTF-8")),
						new PreCorrection.ReplaceUndefinedEntities());
	}

	@Test
	void checksTheCharsetOfPreCorrections() {
		assertThat(problems(SOURCE + FEED + "preCorrections:\n  - rule: force-encoding\n    charset: NO-SUCH\n"))
			.containsExactly("preCorrections[0].charset INVALID_CHARSET");
		assertThat(problems(SOURCE + FEED + "preCorrections:\n  - rule: force-encoding\n    charset: \"???\"\n"))
			.containsExactly("preCorrections[0].charset INVALID_CHARSET");
		assertThat(problems(SOURCE + FEED + "preCorrections:\n  - rule: force-encoding\n"))
			.containsExactly("preCorrections[0].charset MISSING_REQUIRED");
		assertThat(problems(SOURCE + FEED + "preCorrections:\n  - rule: replace-undefined-entities\n    charset: UTF-8\n"))
			.containsExactly("preCorrections[0].charset UNKNOWN_KEY");
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "yyyy-MM-dd HH:mm:ss", "yyyy년 M월 d일 a h:mm", "yyyy.MM.dd HH:mm", "EEE, d MMM yyyy HH:mm:ss" })
	void acceptsDateFormatsThatHoldTheDateAndTheMinute(String pattern) {
		assertThat(parsed(SOURCE + FEED + "dates:\n  formats: [\"" + pattern + "\"]\n").dates().patterns())
			.extracting(DatePattern::pattern)
			.containsExactly(pattern);
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "yyyy-MM-dd HH", "yyyy-MM-dd", "HH:mm", "yyyy-MM-dd hh:mm", "yyyy-MM-dd HH:mm:ss {",
			"bogus" })
	void rejectsDateFormatsThatDoNotHoldTheDateAndTheMinute(String pattern) {
		assertThat(problems(SOURCE + FEED + "dates:\n  formats: [\"" + pattern + "\"]\n"))
			.containsExactly("dates.formats[0] INVALID_DATE_FORMAT");
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "Asia/Seol", "KST", "+25:00" })
	void rejectsAnUnknownTimeZone(String zone) {
		assertThat(problems(SOURCE + FEED + "dates:\n  timeZone: \"" + zone + "\"\n"))
			.containsExactly("dates.timeZone INVALID_TIME_ZONE");
	}

	@Test
	void rejectsDuplicateFeedKeysAndUrls() {
		assertThat(problems(SOURCE + """
				feeds:
				  - key: all
				    url: https://a.example/rss
				    section: 전체
				  - key: all
				    url: https://b.example/rss
				    section: 속보
				"""))
			.containsExactly("feeds[1].key DUPLICATE_FEED_KEY");
		assertThat(problems(SOURCE + """
				feeds:
				  - key: all
				    url: https://a.example/rss
				    section: 전체
				  - key: breaking
				    url: https://a.example/rss
				    section: 속보
				"""))
			.containsExactly("feeds[1].url DUPLICATE_FEED_URL");
	}

	@Test
	void doesNotTreatTwoInvalidUrlsAsDuplicates() {
		assertThat(problems(SOURCE + """
				feeds:
				  - key: a
				    url: not a url
				    section: 전체
				  - key: b
				    url: ftp://b.example/rss
				    section: 속보
				"""))
			.containsExactly("feeds[0].url INVALID_URL", "feeds[1].url INVALID_URL");
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "Politics", "politics_news", "-all", "all-", "a--b", "a234567890b234567890c234567890d2345678901" })
	void rejectsAFeedKeyOutsideTheSlugForm(String key) {
		assertThat(problems(SOURCE + "feeds:\n  - key: " + key + "\n    url: https://a.example/rss\n    section: 전체\n"))
			.containsExactly("feeds[0].key INVALID_FEED_KEY");
	}

	@Test
	void acceptsAFeedKeyOfFortyCharacters() {
		String key = "a234567890b234567890c234567890d234567890";

		assertThat(key).hasSize(40);
		assertThat(parsed(SOURCE + "feeds:\n  - key: " + key + "\n    url: https://a.example/rss\n    section: 전체\n")
			.feeds()).extracting(FeedSpec::key).containsExactly(new FeedKey(key));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			pollIntervalMinutes: 14                         | pollIntervalMinutes POLL_INTERVAL_TOO_SHORT
			pollIntervalMinutes: 0                          | pollIntervalMinutes POLL_INTERVAL_TOO_SHORT
			pollIntervalMinutes: -15                        | pollIntervalMinutes POLL_INTERVAL_TOO_SHORT
			pollIntervalMinutes: 2147483648                 | pollIntervalMinutes WRONG_TYPE
			pollIntervalMinutes: -2147483649                | pollIntervalMinutes WRONG_TYPE
			pollIntervalMinutes: 15.0                       | pollIntervalMinutes WRONG_TYPE
			'pollIntervalMinutes: "30"'                     | pollIntervalMinutes WRONG_TYPE
			""")
	void rejectsASourcePollIntervalBelowTheMinimumOrOfTheWrongType(String line, String problem) {
		assertThat(problems(SOURCE + FEED + line + "\n")).containsExactly(problem);
	}

	@ParameterizedTest
	@CsvSource({ "15, PT15M", "2147483647, PT35791394H7M" })
	void acceptsPollIntervalsFromTheMinimumToTheIntegerLimit(String minutes, Duration expected) {
		CollectionProfile profile = parsed(SOURCE + "feeds:\n  - key: all\n    url: https://a.example/rss\n    section: 전체\n"
				+ "    pollIntervalMinutes: " + minutes + "\npollIntervalMinutes: " + minutes + "\n");

		assertThat(profile.pollInterval()).contains(new PollInterval(expected));
		assertThat(profile.feeds().get(0).pollInterval()).isEqualTo(new PollInterval(expected));
	}

	@Test
	void rejectsAFeedPollIntervalBelowTheMinimum() {
		assertThat(problems(SOURCE + FEED + "    pollIntervalMinutes: 14\n"))
			.containsExactly("feeds[0].pollIntervalMinutes POLL_INTERVAL_TOO_SHORT");
	}

	@Test
	void checksNamespacesAndElementNames() {
		assertThat(problems(SOURCE + FEED + "items:\n  publishedAt: [\"xx:date\"]\n"))
			.containsExactly("items.publishedAt[0] UNKNOWN_NAMESPACE_PREFIX");
		assertThat(problems(SOURCE + FEED + "namespaces:\n  dc: http://example.com/dc/\n"))
			.containsExactly("namespaces.dc PREFIX_REDEFINED");
		assertThat(problems(SOURCE + FEED + "namespaces:\n  ex: relative/path\n"))
			.containsExactly("namespaces.ex INVALID_URL");
		assertThat(problems(SOURCE + FEED + "items:\n  summary: [$other, \"a:b:c\", \"1abc\"]\n")).containsExactly(
				"items.summary[0] INVALID_ELEMENT_NAME", "items.summary[1] INVALID_ELEMENT_NAME",
				"items.summary[2] INVALID_ELEMENT_NAME");
		assertThat(problems(SOURCE + FEED + "items:\n  summary: [$standard, \"dc:date\", $standard]\n"))
			.containsExactly("items.summary[2] DUPLICATE_VALUE_SOURCE");
		assertThat(problems(SOURCE + FEED + "items:\n  body: [$standard]\n")).containsExactly("items.body UNKNOWN_ITEM_FIELD");
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			ftp://www.bloter.net        | INVALID_URL
			www.bloter.net              | INVALID_URL
			/rss                        | INVALID_URL
			'http:///no-host'           | INVALID_URL
			""")
	void rejectsAHomepageThatIsNotAnAbsoluteHttpUrl(String homepage, String code) {
		assertThat(problems("source:\n  name: 블로터\n  homepage: \"" + homepage + "\"\n  language: ko\n" + FEED))
			.containsExactly("source.homepage " + code);
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "xx", "ko-kr", "korean", "und", "ko_KR" })
	void rejectsALanguageThatIsNotACanonicalLanguageTag(String language) {
		assertThat(problems("source:\n  name: 블로터\n  homepage: https://www.bloter.net\n  language: " + language + "\n"
				+ FEED)).containsExactly("source.language INVALID_LANGUAGE");
	}

	@ParameterizedTest
	@CsvSource({ "ko, ko", "ko-KR, ko-KR", "en, en" })
	void acceptsACanonicalLanguageTag(String language, String expectedTag) {
		assertThat(parsed("source:\n  name: 블로터\n  homepage: https://www.bloter.net\n  language: " + language + "\n"
				+ FEED).source().language().toLanguageTag()).isEqualTo(expectedTag);
	}

	@Test
	void rejectsUnknownKeysEverywhere() {
		assertThat(problems(SOURCE + FEED + "script: rm -rf /\n")).containsExactly("script UNKNOWN_KEY");
		assertThat(problems("source:\n  name: 블로터\n  homepage: https://www.bloter.net\n  language: ko\n  logo: x\n" + FEED))
			.containsExactly("source.logo UNKNOWN_KEY");
		assertThat(problems(SOURCE + FEED + "    enabled: true\n")).containsExactly("feeds[0].enabled UNKNOWN_KEY");
		assertThat(problems(SOURCE + FEED + "dates:\n  timezone: UTC\n")).containsExactly("dates.timezone UNKNOWN_KEY");
	}

	@Test
	void rejectsYamlTagsAnchorsAndAliases() {
		assertThat(problems(SOURCE + FEED + "dates:\n  timeZone: !!java.lang.Runtime Asia/Seoul\n"))
			.containsExactly("dates.timeZone TAG_NOT_ALLOWED");
		assertThat(problems("!!str source:\n  name: 블로터\n  homepage: https://www.bloter.net\n  language: ko\n" + FEED))
			.containsExactly(" TAG_NOT_ALLOWED");
		assertThat(problems(SOURCE + FEED + "dates:\n  timeZone: &tz Asia/Seoul\n"))
			.containsExactly("dates.timeZone ANCHOR_NOT_ALLOWED");
		assertThat(problems("source: &s\n  name: 블로터\n  homepage: https://www.bloter.net\n  language: ko\n" + FEED))
			.containsExactly("source ANCHOR_NOT_ALLOWED");
		assertThat(problems("source:\n  name: 블로터\n  homepage: https://www.bloter.net\n  language: *lang\n" + FEED))
			.containsExactly("source.language ALIAS_NOT_ALLOWED");
		assertThat(problems(SOURCE + FEED + "dates:\n  timeZone: *tz\n")).containsExactly("dates.timeZone ALIAS_NOT_ALLOWED");
	}

	@Test
	void rejectsDuplicateKeysNullsAndWrongTypes() {
		assertThat(problems(SOURCE + FEED + "dates:\n  timeZone: Asia/Seoul\n  timeZone: UTC\n"))
			.containsExactly("dates.timeZone DUPLICATE_KEY");
		assertThat(problems(SOURCE + FEED + "dates:\n  timeZone:\n")).containsExactly("dates.timeZone NULL_NOT_ALLOWED");
		assertThat(problems(SOURCE + FEED + "preCorrections: null\n")).containsExactly("preCorrections NULL_NOT_ALLOWED");
		assertThat(problems(SOURCE + "feeds: all\n")).containsExactly("feeds WRONG_TYPE");
		assertThat(problems(SOURCE + FEED + "dates:\n  formats: yyyy-MM-dd HH:mm\n")).containsExactly("dates.formats WRONG_TYPE");
		assertThat(problems("source: [a]\n" + FEED)).containsExactly("source WRONG_TYPE");
		assertThat(problems(SOURCE + FEED + "? [a, b]\n: c\n")).containsExactly(" WRONG_TYPE");
	}

	@Test
	void reportsTheLocationOfEachProblem() {
		List<ProfileProblem> problems = invalid(SOURCE + FEED + "dates:\n  timeZone: Asia/Seol\n");

		assertThat(problems).hasSize(1);
		assertThat(problems.get(0).location()).isEqualTo(new TextLocation(10, 13));
	}

	@Test
	void reportsAllProblemsOfTheSameStageAtOnce() {
		assertThat(problems("""
				source:
				  name: 블로터
				  homepage: ftp://www.bloter.net
				  language: xx
				feeds:
				  - key: Politics
				    url: https://a.example/rss
				    section: 정치
				    pollIntervalMinutes: 5
				preCorrections:
				  - rule: run-script
				dates:
				  formats: ["yyyy-MM-dd"]
				  timeZone: Asia/Seol
				""")).containsExactly("source.homepage INVALID_URL", "source.language INVALID_LANGUAGE",
				"feeds[0].key INVALID_FEED_KEY", "feeds[0].pollIntervalMinutes POLL_INTERVAL_TOO_SHORT",
				"preCorrections[0].rule UNKNOWN_PRE_CORRECTION", "dates.timeZone INVALID_TIME_ZONE",
				"dates.formats[0] INVALID_DATE_FORMAT");
	}

	@Test
	void stopsBeforeMeaningChecksWhenTheShapeIsWrong() {
		assertThat(problems("""
				source:
				  name: 블로터
				  homepage: ftp://www.bloter.net
				  language: ko
				feeds: all
				""")).containsExactly("feeds WRONG_TYPE");
	}

	@Test
	void passesOnAProblemOfTheReader() {
		assertThat(problems("a: [1, 2\n")).containsExactly(" SYNTAX_ERROR");
	}

	private CollectionProfile parsed(String text) {
		ProfileParse parse = this.parser.parseOperatorProfile(text);
		assertThat(parse).as("%s", parse).isInstanceOf(ProfileParse.Parsed.class);
		return ((ProfileParse.Parsed) parse).profile();
	}

	private List<ProfileProblem> invalid(String text) {
		ProfileParse parse = this.parser.parseOperatorProfile(text);
		assertThat(parse).isInstanceOf(ProfileParse.Invalid.class);
		return ((ProfileParse.Invalid) parse).problems();
	}

	/** 문제마다 "경로 종류" — 최상위 경로는 빈 문자열이다 */
	private List<String> problems(String text) {
		return invalid(text).stream().map(problem -> problem.path() + " " + problem.code()).toList();
	}

	private static ValueSource element(String namespaceUri, String localName) {
		return new ValueSource.Element(new ElementName(namespaceUri, localName));
	}

}
