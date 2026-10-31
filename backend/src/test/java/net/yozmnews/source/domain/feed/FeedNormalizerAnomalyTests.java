package net.yozmnews.source.domain.feed;

import static net.yozmnews.source.domain.feed.FeedFixtures.FETCHED_AT;
import static net.yozmnews.source.domain.feed.FeedFixtures.normalized;
import static net.yozmnews.source.domain.feed.FeedFixtures.unreadable;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import net.yozmnews.source.DocumentProblemKind;
import net.yozmnews.source.FetchedArticle;
import org.junit.jupiter.api.Test;

/**
 * 조사 자료의 형식 이상 사례와 문서 단위 결과 — 합성 픽스처를 프로필에 따라 정규화한다.
 * <p>
 * 지키는 규칙: source-R38 · source-R40 · source-R41 · source-R42 · source-R44
 */
class FeedNormalizerAnomalyTests {

	private static final Instant KST_0931 = Instant.parse("2026-10-25T00:31:38Z");

	@Test
	void readsDcDateThroughTheProfileMapping() {
		FetchedArticle article = only(normalized("anomaly-dc-date", """
				items:
				  publishedAt: [$standard, "dc:date"]
				"""));

		assertThat(article.publishedAt()).isEqualTo(KST_0931);
		assertThat(article.publishedAtEstimated()).isFalse();
	}

	@Test
	void dcDateIsNotStandardInRss20() {
		// 대조 — 매핑이 없으면 dc:date 를 읽지 않아 추정한다(매핑 경로가 실제로 쓰였다는 증거)
		FetchedArticle article = only(normalized("anomaly-dc-date", ""));

		assertThat(article.publishedAt()).isEqualTo(FETCHED_AT);
		assertThat(article.publishedAtEstimated()).isTrue();
	}

	@Test
	void aPublishedAtTooFarInTheFutureFallsBackToTheNextSource() {
		// pubDate 는 받은 시각(2026-10-26T00:00:00Z)보다 9시간 미래라 해석 실패로 보고 다음 읽을 곳 dc:date 를 쓴다
		byte[] body = ("<rss version=\"2.0\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><channel><title>t</title>"
				+ "<item><title>미래 pubDate</title><link>https://alpha.example/news/f1</link>"
				+ "<pubDate>Mon, 26 Oct 2026 09:00:00 GMT</pubDate><dc:date>2026-10-25T09:31:38+09:00</dc:date></item>"
				+ "</channel></rss>")
			.getBytes(StandardCharsets.UTF_8);

		FetchedArticle mapped = only((FeedNormalization.Normalized) FeedFixtures.normalize(body, """
				items:
				  publishedAt: [$standard, "dc:date"]
				"""));
		assertThat(mapped.publishedAt()).isEqualTo(KST_0931);
		assertThat(mapped.publishedAtEstimated()).isFalse();
		// 대조 — 다음 읽을 곳이 없으면 받은 시각으로 추정한다
		FetchedArticle standardOnly = only((FeedNormalization.Normalized) FeedFixtures.normalize(body, ""));
		assertThat(standardOnly.publishedAt()).isEqualTo(FETCHED_AT);
		assertThat(standardOnly.publishedAtEstimated()).isTrue();
	}

	@Test
	void readsANonRfc822DateWithAProfilePattern() {
		assertThat(only(normalized("anomaly-plain-date", """
				dates:
				  formats: ["yyyy-MM-dd HH:mm:ss"]
				""")).publishedAt()).isEqualTo(KST_0931);
		// 대조 — 패턴이 없으면 추정
		assertThat(only(normalized("anomaly-plain-date", "")).publishedAtEstimated()).isTrue();
	}

	@Test
	void readsAKoreanDateWithAProfilePattern() {
		FetchedArticle article = only(normalized("anomaly-korean-date", """
				dates:
				  formats: ["yyyy년 M월 d일 a h:mm"]
				"""));

		assertThat(article.publishedAt()).isEqualTo(Instant.parse("2024-10-29T06:05:00Z"));
		assertThat(article.publishedAtEstimated()).isFalse();
	}

	@Test
	void estimatesAMissingPublishedAt() {
		assertThat(only(normalized("anomaly-no-date", ""))).isEqualTo(new FetchedArticle("발행 시각 없음",
				URI.create("https://alpha.example/news/d4"), null, "요약", FETCHED_AT, true, null, List.of(), null));
	}

	@Test
	void replacesAnUndefinedEntityWithTheProfileRule() {
		assertThat(only(normalized("anomaly-undefined-entity", """
				preCorrections:
				  - rule: replace-undefined-entities
				""")).title()).isEqualTo("A B");
	}

	@Test
	void anUndefinedEntityStopsTheDocumentWithoutTheRule() {
		assertThat(unreadable("anomaly-undefined-entity", "").problem())
			.isEqualTo(new DocumentProblem(DocumentProblemKind.MALFORMED_XML, "XML 형식이 틀렸다", 9, 15));
	}

	@Test
	void keepsOnlyTheFrontOfAFullBodyContent() {
		FetchedArticle article = only(normalized("anomaly-full-body", """
				items:
				  summary: ["content:encoded"]
				"""));

		assertThat(article.summary()).isEqualTo("가나다라마바사아자차".repeat(100));
		assertThat(article.summary()).hasSize(1_000).startsWith("가나다라마바사아자차가나다라마바사아자차")
			.endsWith("가나다라마바사아자차가나다라마바사아자차");
	}

	@Test
	void fallsBackToContentWhenTheSummaryIsEmpty() {
		List<FetchedArticle> articles = normalized("anomaly-empty-summary", """
				items:
				  summary: [$standard, "content:encoded"]
				""").articles();

		assertThat(articles).extracting(FetchedArticle::summary).containsExactly("본문 앞부분", null);
	}

	@Test
	void readsAnItemWithOnlyTitleAndLink() {
		assertThat(normalized("anomaly-title-link-only", "").articles()).containsExactly(new FetchedArticle("제목과 링크만",
				URI.create("https://alpha.example/news/t1"), null, null, FETCHED_AT, true, null, List.of(), null));
	}

	@Test
	void reportsAnUnsupportedDeclaredEncoding() {
		FeedNormalization result = FeedFixtures
			.normalize("<?xml version=\"1.0\" encoding=\"x-unknown\"?><rss/>".getBytes(StandardCharsets.US_ASCII), "");

		assertThat(result).isEqualTo(new FeedNormalization.Unreadable(new DocumentProblem(
				DocumentProblemKind.UNSUPPORTED_ENCODING, "문서가 선언한 문자 인코딩을 지원하지 않는다: x-unknown", null, null)));
	}

	@Test
	void reportsARejectedDtdDeclaration() {
		FeedNormalization result = FeedFixtures.normalize(
				"<!DOCTYPE rss [<!ENTITY a \"A\">]><rss version=\"2.0\"><channel/></rss>".getBytes(StandardCharsets.UTF_8),
				"");

		assertThat(result).isEqualTo(new FeedNormalization.Unreadable(new DocumentProblem(
				DocumentProblemKind.DTD_DECLARATION, "DTD 선언과 선언되지 않은 엔티티는 받지 않는다: 엔티티 선언 a", 1, 31)));
	}

	@Test
	void carriesTheReplacedCharacterCount() {
		byte[] head = "<rss version=\"2.0\"><channel><title>t</title><item><title>A".getBytes(StandardCharsets.US_ASCII);
		byte[] tail = "B</title><link>https://alpha.example/1</link></item></channel></rss>"
			.getBytes(StandardCharsets.US_ASCII);
		byte[] body = new byte[head.length + 1 + tail.length];
		System.arraycopy(head, 0, body, 0, head.length);
		body[head.length] = (byte) 0xFF;
		System.arraycopy(tail, 0, body, head.length + 1, tail.length);

		FeedNormalization result = FeedFixtures.normalize(body, "");

		assertThat(result).isInstanceOf(FeedNormalization.Normalized.class);
		FeedNormalization.Normalized normalized = (FeedNormalization.Normalized) result;
		assertThat(normalized.replacedCharacters()).isEqualTo(1);
		assertThat(normalized.articles()).extracting(FetchedArticle::title).containsExactly("A�B");
	}

	@Test
	void readsAnEmptyFeed() {
		FeedNormalization result = FeedFixtures.normalize(
				"<rss version=\"2.0\"><channel><title>t</title><link>https://alpha.example</link><description>d</description></channel></rss>"
					.getBytes(StandardCharsets.UTF_8),
				"");

		assertThat(result).isEqualTo(new FeedNormalization.Normalized(List.of(), List.of(), 0, 122));
	}

	@Test
	void countsDecodedCodePointsWithoutTheByteOrderMark() {
		byte[] text = "<rss version=\"2.0\"><channel><title>한\uD83D\uDE00</title><link>https://alpha.example</link><description>d</description></channel></rss>"
			.getBytes(StandardCharsets.UTF_8);
		byte[] body = new byte[3 + text.length];
		body[0] = (byte) 0xEF;
		body[1] = (byte) 0xBB;
		body[2] = (byte) 0xBF;
		System.arraycopy(text, 0, body, 3, text.length);

		FeedNormalization result = FeedFixtures.normalize(body, "");

		// readsAnEmptyFeed 의 122자에서 제목 t(1자)를 한 · U+1F600(코드 포인트 2, UTF-16 단위 3)으로 바꿨다 — BOM 은 세지 않는다
		assertThat(result).isEqualTo(new FeedNormalization.Normalized(List.of(), List.of(), 0, 123));
	}

	private static FetchedArticle only(FeedNormalization.Normalized normalized) {
		assertThat(normalized.articles()).hasSize(1);
		assertThat(normalized.skipped()).isEmpty();
		return normalized.articles().get(0);
	}

}
