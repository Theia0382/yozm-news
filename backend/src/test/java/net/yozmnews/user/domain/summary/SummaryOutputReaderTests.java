package net.yozmnews.user.domain.summary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.platform.StructuredOutputSchema;
import org.junit.jupiter.api.Test;

/**
 * 정리 출력의 서술과 읽기 — 흐름 전체의 글은 언제나, 꼭대기가 둘 이상이면 꼭대기 별칭마다의 글도 필수다. 칸이 빠지거나 비었으면 읽지 못한 출력이다 — 일부만
 * 읽지 않는다. 상한(제목 120 · 흐름 요약 1,200 · 최근 전개 800 코드 포인트)을 넘은 글은 상한까지만 남긴다.
 */
class SummaryOutputReaderTests {

	private static final String WHOLE = "\"whole\":{\"title\":\"전체 제목\",\"summary\":\"전체 요약\",\"recent\":\"전체 최근\"}";

	@Test
	void aSingleTopReadsTheWholeAsThatTopsText() {
		assertThat(SummaryOutputReader.read("{" + WHOLE + "}", List.of("T1")))
			.isEqualTo(new SummaryRead.Read(new SummaryText("전체 제목", "전체 요약", "전체 최근"),
					List.of(new SummaryText("전체 제목", "전체 요약", "전체 최근"))));
	}

	@Test
	void severalTopsAreReadInAliasOrderWhateverTheKeyOrder() {
		String text = "{\"tops\":{\"T2\":{\"title\":\"나 제목\",\"summary\":\"나 요약\",\"recent\":\"나 최근\"},"
				+ "\"T1\":{\"title\":\"가 제목\",\"summary\":\"가 요약\",\"recent\":\"가 최근\"}}," + WHOLE + "}";

		assertThat(SummaryOutputReader.read(text, List.of("T1", "T2")))
			.isEqualTo(new SummaryRead.Read(new SummaryText("전체 제목", "전체 요약", "전체 최근"),
					List.of(new SummaryText("가 제목", "가 요약", "가 최근"), new SummaryText("나 제목", "나 요약", "나 최근"))));
	}

	@Test
	void aMissingTopMakesTheWholeOutputUnreadable() {
		String text = "{" + WHOLE + ",\"tops\":{\"T1\":{\"title\":\"가\",\"summary\":\"가\",\"recent\":\"가\"}}}";

		assertThat(SummaryOutputReader.read(text, List.of("T1", "T2")))
			.isEqualTo(new SummaryRead.Unreadable("$.tops.T2: 없다"));
	}

	@Test
	void severalTopsWithoutTheTopsFieldAreUnreadable() {
		assertThat(SummaryOutputReader.read("{" + WHOLE + "}", List.of("T1", "T2")))
			.isInstanceOf(SummaryRead.Unreadable.class);
	}

	@Test
	void aMissingOrBlankFieldIsUnreadable() {
		assertThat(SummaryOutputReader.read("{\"whole\":{\"title\":\"제목\",\"summary\":\"요약\"}}", List.of("T1")))
			.isInstanceOf(SummaryRead.Unreadable.class);
		assertThat(SummaryOutputReader.read("{\"whole\":{\"title\":\" \",\"summary\":\"요약\",\"recent\":\"최근\"}}",
				List.of("T1")))
			.isInstanceOf(SummaryRead.Unreadable.class);
	}

	@Test
	void textAtTheLimitIsReadWholeAndTextOverTheLimitIsCutToIt() {
		assertThat(SummaryOutputReader.read(whole("가".repeat(120), "나".repeat(1_200), "다".repeat(800)), List.of("T1")))
			.isEqualTo(read("가".repeat(120), "나".repeat(1_200), "다".repeat(800)));
		assertThat(SummaryOutputReader.read(whole("가".repeat(121), "나".repeat(1_201), "다".repeat(801)), List.of("T1")))
			.isEqualTo(read("가".repeat(120), "나".repeat(1_200), "다".repeat(800)));
		assertThat(SummaryOutputReader.read(whole("가".repeat(5_000), "나", "다"), List.of("T1")))
			.isEqualTo(read("가".repeat(120), "나", "다"));
	}

	@Test
	void textThatIsNotJsonIsUnreadable() {
		assertThat(SummaryOutputReader.read("정리했습니다.", List.of("T1"))).isInstanceOf(SummaryRead.Unreadable.class);
	}

	@Test
	void theSchemaDocumentOfASingleTopHasOnlyTheWhole() {
		assertThat(StructuredOutputSchema.json(SummaryOutputSchema.shape(List.of("T1")))).isEqualTo(
				"{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"object\",\"properties\":{"
						+ "\"whole\":{\"type\":\"object\",\"properties\":{"
						+ "\"title\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":120},"
						+ "\"summary\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":1200},"
						+ "\"recent\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":800}},"
						+ "\"required\":[\"title\",\"summary\",\"recent\"],\"additionalProperties\":false}},"
						+ "\"required\":[\"whole\"],\"additionalProperties\":false}");
	}

	@Test
	void theSchemaDocumentOfSeveralTopsRequiresEveryAlias() {
		String schema = StructuredOutputSchema.json(SummaryOutputSchema.shape(SummaryOutputSchema.aliases(2)));

		assertThat(schema).contains("\"required\":[\"whole\",\"tops\"]").contains("\"required\":[\"T1\",\"T2\"]");
	}

	@Test
	void aliasesAreNumberedFromOne() {
		assertThat(SummaryOutputSchema.aliases(3)).containsExactly("T1", "T2", "T3");
	}

	private static SummaryRead read(String title, String summary, String recent) {
		SummaryText text = new SummaryText(title, summary, recent);
		return new SummaryRead.Read(text, List.of(text));
	}

	private static String whole(String title, String summary, String recent) {
		return "{\"whole\":{\"title\":\"" + title + "\",\"summary\":\"" + summary + "\",\"recent\":\"" + recent + "\"}}";
	}

}
