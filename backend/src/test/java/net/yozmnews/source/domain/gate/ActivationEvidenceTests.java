package net.yozmnews.source.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import net.yozmnews.source.domain.feed.FeedFixtures;
import net.yozmnews.source.domain.feed.FeedNormalization;
import net.yozmnews.source.domain.fetch.FetchResult;
import net.yozmnews.source.domain.fetch.FetchState;
import org.junit.jupiter.api.Test;

/**
 * 활성화 기준의 근거 — 조사 자료(docs/research/2026-10-rss-survey.md)의 형식 이상 사례를 재현한 합성 피드를 정규화해 판정한다.
 * 프로필 설정 실수는 기준에 못 미치고, 같은 피드에 맞는 프로필은 통과한다. 피드 파일은 {@code src/test/resources/feeds/} 이고 문자셋
 * 사례의 문서는 이 시험이 만든다.
 * <p>
 * 지키는 규칙: source-R9
 */
class ActivationEvidenceTests {

	private static final String PUBLISHED_AT_UNREAD = """
			items:
			  publishedAt: []
			""";

	@Test
	void rejectsADcDateFeedUntilTheProfileMapsIt() {
		assertThat(criteria("anomaly-dc-date", "")).containsExactly(Criterion.DATED_ARTICLES);
		assertThat(criteria("anomaly-dc-date", """
				items:
				  publishedAt: [$standard, "dc:date"]
				""")).isEmpty();
	}

	@Test
	void rejectsNonStandardDatesUntilTheProfileDeclaresTheirFormat() {
		assertThat(criteria("anomaly-korean-date", "")).containsExactly(Criterion.DATED_ARTICLES);
		assertThat(criteria("anomaly-korean-date", """
				dates:
				  formats: ["yyyy년 M월 d일 a h:mm"]
				""")).isEmpty();
		assertThat(criteria("anomaly-plain-date", "")).containsExactly(Criterion.DATED_ARTICLES);
		assertThat(criteria("anomaly-plain-date", """
				dates:
				  formats: ["yyyy-MM-dd HH:mm:ss"]
				""")).isEmpty();
	}

	@Test
	void acceptsAFeedWithoutDatesOnlyWhenTheProfileSaysSo() {
		assertThat(criteria("anomaly-no-date", PUBLISHED_AT_UNREAD)).isEmpty();
		// 대조 — 선언하지 않으면 날짜 기준에 못 미친다
		assertThat(criteria("anomaly-no-date", "")).containsExactly(Criterion.DATED_ARTICLES);
	}

	@Test
	void doesNotJudgeTheSummary() {
		// 제목과 링크만 있는 항목 — 요약문이 없어도 요약문 기준은 없다. 이 픽스처는 발행 시각도 없어 날짜만 걸린다
		PreviewOutcome outcome = outcome("anomaly-title-link-only", PUBLISHED_AT_UNREAD);

		assertThat(outcome).isInstanceOfSatisfying(PreviewOutcome.Passed.class,
				passed -> assertThat(passed.preview().summaries()).isEqualTo(new SummaryLengths(0, 1, null, null, null)));
		assertThat(criteria("anomaly-title-link-only", "")).containsExactly(Criterion.DATED_ARTICLES);
	}

	@Test
	void rejectsAFeedWhoseItemsAreMostlySkipped() {
		// 항목 7 중 6 을 건너뛴다 — 남은 기사 하나에는 발행 시각이 없다
		assertThat(criteria("skipped-items", "")).containsExactly(Criterion.SKIPPED_ITEMS, Criterion.DATED_ARTICLES);
	}

	@Test
	void rejectsADocumentReadWithTheWrongCharset() {
		PreviewOutcome outcome = outcome(koreanFeed(), """
				preCorrections:
				  - rule: force-encoding
				    charset: EUC-KR
				""");

		assertThat(outcome).isInstanceOfSatisfying(PreviewOutcome.BelowCriteria.class,
				below -> assertThat(below.shortfalls()).extracting(Shortfall::criterion)
					.containsExactly(Criterion.REPLACED_CHARACTERS));
		// 대조 — 같은 문서를 선언대로(UTF-8) 읽으면 통과한다
		assertThat(outcome(koreanFeed(), "")).isInstanceOf(PreviewOutcome.Passed.class);
	}

	@Test
	void acceptsALocallyBrokenByte() {
		byte[] body = koreanFeed();
		int marker = indexOf(body, " x ".getBytes(StandardCharsets.US_ASCII)) + 1;
		body[marker] = (byte) 0xFF;

		PreviewOutcome outcome = outcome(body, "");

		assertThat(outcome).isInstanceOfSatisfying(PreviewOutcome.Passed.class, passed -> {
			assertThat(passed.preview().replacedCharacters()).isEqualTo(1);
			assertThat(passed.preview().decodedCharacters()).isGreaterThanOrEqualTo(2_000);
		});
	}

	private static List<Criterion> criteria(String fixture, String profile) {
		return switch (outcome(fixture, profile)) {
			case PreviewOutcome.Passed passed -> List.of();
			case PreviewOutcome.BelowCriteria below -> below.shortfalls().stream().map(Shortfall::criterion).toList();
			case PreviewOutcome.Unfetched unfetched -> throw new AssertionError("받지 못했다: " + unfetched);
		};
	}

	private static PreviewOutcome outcome(String fixture, String profile) {
		return FeedGate.preview(fetched(FeedFixtures.normalized(fixture, profile)), FeedFixtures.profile(profile));
	}

	private static PreviewOutcome outcome(byte[] body, String profile) {
		FeedNormalization result = FeedFixtures.normalize(body, profile);
		assertThat(result).as("%s", result).isInstanceOf(FeedNormalization.Normalized.class);
		return FeedGate.preview(fetched((FeedNormalization.Normalized) result), FeedFixtures.profile(profile));
	}

	private static FetchResult.Fetched fetched(FeedNormalization.Normalized normalized) {
		return new FetchResult.Fetched(normalized, FetchState.NONE, FeedFixtures.FETCHED_AT, null);
	}

	/**
	 * 한글 UTF-8 합성 피드 — 기사 12건, 2,000 코드 포인트 이상. 한글은 텍스트 안에서 공백 · 숫자 · 마침표 · {@code <} 옆에만 둔다 — 다른
	 * 문자셋으로 읽어도 마크업이 깨지지 않게. 첫 기사 요약문에 손상 자리 표식 {@code " x "} 가 있다.
	 */
	private static byte[] koreanFeed() {
		StringBuilder text = new StringBuilder("""
				<?xml version="1.0" encoding="UTF-8"?>
				<rss version="2.0"><channel><title>합성 피드</title><link>https://alpha.example/</link>
				<description>합성 피드</description>
				""");
		String sentence = "가나다라마바사아자차카타파하 ".repeat(7);
		for (int i = 0; i < 12; i++) {
			text.append("<item><title>합성 기사 ").append(i).append("</title><link>https://alpha.example/news/")
				.append(i).append("</link><pubDate>Sun, 25 Oct 2026 09:31:38 +0900</pubDate><description>")
				.append(i == 0 ? "표식 x " : "")
				.append(sentence)
				.append("</description></item>\n");
		}
		text.append("</channel></rss>\n");
		return text.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static int indexOf(byte[] body, byte[] part) {
		for (int i = 0; i + part.length <= body.length; i++) {
			boolean match = true;
			for (int j = 0; j < part.length && match; j++) {
				match = body[i + j] == part[j];
			}
			if (match) {
				return i;
			}
		}
		throw new AssertionError("표식이 없다");
	}

}
