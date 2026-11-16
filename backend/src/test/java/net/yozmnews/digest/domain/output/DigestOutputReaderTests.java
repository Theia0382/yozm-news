package net.yozmnews.digest.domain.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 출력 원문 읽기 — 코드 펜스 · JSON 하나 · 필수 · 타입 · 상한과 넘을 때의 처분. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: digest-R4 · digest-R5
 * <p>
 * 원문과 기대값은 손으로 쓴 리터럴이다. 길이 경계는 한글 · 보충 평면 문자(UTF-16 두 칸)로 코드 포인트 수를 센다.
 */
class DigestOutputReaderTests {

	private static final String NORMAL = """
			{
			  "overview": " 오늘의 흐름 ",
			  "top_stories": [
			    { "title": "제목 하나", "summary": "요약 하나", "refs": ["A1", "A2"] }
			  ],
			  "more_headlines": [
			    { "ref": "A3", "note": "한 줄" },
			    { "ref": "A4" }
			  ]
			}
			""";

	/** 보충 평면 문자 하나 — UTF-16 두 칸, 코드 포인트 하나 */
	private static final String WIDE = "𝐀";

	@Test
	void readsANormalOutputAndStripsValues() {
		OutputRead read = DigestOutputReader.read(NORMAL);

		assertThat(read).isEqualTo(new OutputRead.Read(new DigestDraft("오늘의 흐름",
				List.of(new StoryDraft("제목 하나", "요약 하나", List.of("A1", "A2"))),
				List.of(new HeadlineDraft("A3", "한 줄"), new HeadlineDraft("A4", null))), 0));
	}

	@Test
	void unwrapsOneCodeFence() {
		assertThat(DigestOutputReader.read("```json\n" + NORMAL + "```")).isInstanceOf(OutputRead.Read.class);
		assertThat(DigestOutputReader.read("  ```\n" + NORMAL + "```\n")).isInstanceOf(OutputRead.Read.class);
	}

	@Test
	void doesNotUnwrapTwoCodeFences() {
		assertThat(DigestOutputReader.read("```json\n```json\n" + NORMAL + "```\n```"))
			.isEqualTo(new OutputRead.Unreadable("JSON 이 아니다"));
	}

	@Test
	void rejectsSomethingThatIsNotOneJsonObject() {
		assertThat(DigestOutputReader.read("오늘은 조용했다.")).isEqualTo(new OutputRead.Unreadable("JSON 이 아니다"));
		assertThat(DigestOutputReader.read("")).isEqualTo(new OutputRead.Unreadable("JSON 이 아니다"));
		assertThat(DigestOutputReader.read("[" + NORMAL + "]")).isEqualTo(new OutputRead.Unreadable("$: 객체가 아니다"));
		assertThat(DigestOutputReader.read(NORMAL + " {}")).isEqualTo(new OutputRead.Unreadable("JSON 이 아니다"));
	}

	@Test
	void rejectsADuplicateKey() {
		String twice = """
				{ "overview": "하나", "overview": "둘", "top_stories": [], "more_headlines": [] }
				""";

		assertThat(DigestOutputReader.read(twice)).isEqualTo(new OutputRead.Unreadable("JSON 이 아니다"));
	}

	@Test
	void rejectsAMissingOrBlankOverview() {
		assertThat(DigestOutputReader.read("""
				{ "top_stories": [], "more_headlines": [] }
				""")).isEqualTo(new OutputRead.Unreadable("$.overview: 없다"));
		assertThat(DigestOutputReader.read("""
				{ "overview": "  ", "top_stories": [], "more_headlines": [] }
				""")).isEqualTo(new OutputRead.Unreadable("$.overview: 비었다"));
		assertThat(DigestOutputReader.read("""
				{ "overview": null, "top_stories": [], "more_headlines": [] }
				""")).isEqualTo(new OutputRead.Unreadable("$.overview: null 이다"));
	}

	@Test
	void rejectsAWrongTypeWithoutConverting() {
		String numberRef = """
				{ "overview": "흐름", "top_stories": [ { "title": "t", "summary": "s", "refs": [1] } ], "more_headlines": [] }
				""";
		String stringRef = """
				{ "overview": "흐름", "top_stories": [ { "title": "t", "summary": "s", "refs": ["A1"] } ], "more_headlines": [] }
				""";

		assertThat(DigestOutputReader.read(numberRef))
			.isEqualTo(new OutputRead.Unreadable("$.top_stories[0].refs[0]: 문자열이 아니다"));
		assertThat(DigestOutputReader.read(stringRef)).isInstanceOf(OutputRead.Read.class);
		assertThat(DigestOutputReader.read("""
				{ "overview": "흐름", "top_stories": {}, "more_headlines": [] }
				""")).isEqualTo(new OutputRead.Unreadable("$.top_stories: 배열이 아니다"));
	}

	@Test
	void ignoresUnknownProperties() {
		String extra = """
				{ "overview": "흐름", "mood": "맑음", "top_stories": [], "more_headlines": [ { "ref": "A1", "url": "x" } ] }
				""";

		assertThat(DigestOutputReader.read(extra)).isEqualTo(new OutputRead.Read(
				new DigestDraft("흐름", List.of(), List.of(new HeadlineDraft("A1", null))), 0));
	}

	@Test
	void takesANullOrBlankNoteAsNone() {
		String notes = """
				{ "overview": "흐름", "top_stories": [], "more_headlines": [ { "ref": "A1", "note": null }, { "ref": "A2", "note": " " } ] }
				""";

		assertThat(DigestOutputReader.read(notes)).isEqualTo(new OutputRead.Read(new DigestDraft("흐름", List.of(),
				List.of(new HeadlineDraft("A1", null), new HeadlineDraft("A2", null))), 0));
	}

	@Test
	void dropsOnlyTheStoryWhoseTitleIsOverTheLimit() {
		String atLimit = "가".repeat(199) + WIDE;
		String overLimit = WIDE.repeat(201);
		String output = """
				{ "overview": "흐름", "top_stories": [
				    { "title": "%s", "summary": "s", "refs": ["A1"] },
				    { "title": "%s", "summary": "s", "refs": ["A2"] }
				  ], "more_headlines": [] }
				""".formatted(atLimit, overLimit);

		assertThat(DigestOutputReader.read(output)).isEqualTo(new OutputRead.Read(
				new DigestDraft("흐름", List.of(new StoryDraft(atLimit, "s", List.of("A1"))), List.of()), 1));
	}

	@Test
	void failsOnAMissingPropertyEvenBesideAnOverLimitTitle() {
		String missingSummary = """
				{ "overview": "흐름", "top_stories": [ { "title": "%s", "refs": ["A1"] } ], "more_headlines": [] }
				""".formatted("가".repeat(201));
		String withSummary = """
				{ "overview": "흐름", "top_stories": [ { "title": "%s", "summary": "s", "refs": ["A1"] } ], "more_headlines": [] }
				""".formatted("가".repeat(201));

		assertThat(DigestOutputReader.read(withSummary))
			.isEqualTo(new OutputRead.Read(new DigestDraft("흐름", List.of(), List.of()), 1));
		assertThat(DigestOutputReader.read(missingSummary))
			.isEqualTo(new OutputRead.Unreadable("$.top_stories[0].summary: 없다"));
	}

	@Test
	void countsADroppedHeadlineOnceEvenWhenItsNoteIsAlsoOverTheLimit() {
		String output = """
				{ "overview": "흐름", "top_stories": [], "more_headlines": [ { "ref": "%s", "note": "%s" }, { "ref": "A2" } ] }
				""".formatted("A".repeat(17), "가".repeat(301));

		assertThat(DigestOutputReader.read(output)).isEqualTo(
				new OutputRead.Read(new DigestDraft("흐름", List.of(), List.of(new HeadlineDraft("A2", null))), 1));
	}

	@Test
	void dropsTheStoryWhoseSummaryIsOverTheLimit() {
		String output = """
				{ "overview": "흐름", "top_stories": [ { "title": "t", "summary": "%s", "refs": ["A1"] } ], "more_headlines": [] }
				""".formatted("가".repeat(2_001));

		assertThat(DigestOutputReader.read(output))
			.isEqualTo(new OutputRead.Read(new DigestDraft("흐름", List.of(), List.of()), 1));
	}

	@Test
	void failsWhenTheOverviewIsOverTheLimit() {
		String atLimit = """
				{ "overview": "%s", "top_stories": [], "more_headlines": [] }
				""".formatted(WIDE.repeat(1_000));
		String overLimit = """
				{ "overview": "%s", "top_stories": [], "more_headlines": [] }
				""".formatted(WIDE.repeat(1_001));

		assertThat(DigestOutputReader.read(atLimit)).isInstanceOf(OutputRead.Read.class);
		assertThat(DigestOutputReader.read(overLimit))
			.isEqualTo(new OutputRead.Unreadable("$.overview: 1000 코드 포인트를 넘는다"));
	}

	@Test
	void dropsOnlyANoteThatIsOverTheLimit() {
		String output = """
				{ "overview": "흐름", "top_stories": [], "more_headlines": [ { "ref": "A1", "note": "%s" }, { "ref": "A2", "note": "%s" } ] }
				""".formatted("가".repeat(301), "가".repeat(300));

		assertThat(DigestOutputReader.read(output)).isEqualTo(new OutputRead.Read(new DigestDraft("흐름", List.of(),
				List.of(new HeadlineDraft("A1", null), new HeadlineDraft("A2", "가".repeat(300)))), 1));
	}

	@Test
	void readsOnlyTheFirstItemsOfALongArray() {
		StringBuilder stories = new StringBuilder();
		for (int index = 1; index <= 21; index++) {
			if (index > 1) {
				stories.append(',');
			}
			stories.append("{ \"title\": \"t").append(index).append("\", \"summary\": \"s\", \"refs\": [\"A1\"] }");
		}
		String output = "{ \"overview\": \"흐름\", \"top_stories\": [" + stories + "], \"more_headlines\": [] }";

		OutputRead read = DigestOutputReader.read(output);

		assertThat(read).isInstanceOf(OutputRead.Read.class);
		OutputRead.Read result = (OutputRead.Read) read;
		assertThat(result.overLimit()).isEqualTo(1);
		assertThat(result.draft().topStories()).hasSize(20);
		assertThat(result.draft().topStories().get(19).title()).isEqualTo("t20");
	}

	@Test
	void dropsAnAliasThatIsOverTheLimit() {
		String output = """
				{ "overview": "흐름", "top_stories": [ { "title": "t", "summary": "s", "refs": ["A1", "%s"] } ],
				  "more_headlines": [ { "ref": "%s" }, { "ref": "A2" } ] }
				""".formatted("A".repeat(17), "A".repeat(17));

		assertThat(DigestOutputReader.read(output)).isEqualTo(new OutputRead.Read(
				new DigestDraft("흐름", List.of(new StoryDraft("t", "s", List.of("A1"))),
						List.of(new HeadlineDraft("A2", null))),
				2));
	}

	@Test
	void keepsAnEmptyAliasForTheCitationCheck() {
		String output = """
				{ "overview": "흐름", "top_stories": [ { "title": "t", "summary": "s", "refs": ["A1", " "] } ],
				  "more_headlines": [ { "ref": "" }, { "ref": "A2" } ] }
				""";

		assertThat(DigestOutputReader.read(output)).isEqualTo(new OutputRead.Read(
				new DigestDraft("흐름", List.of(new StoryDraft("t", "s", List.of("A1", ""))),
						List.of(new HeadlineDraft("", null), new HeadlineDraft("A2", null))),
				0));
	}

	@Test
	void stillFailsOnABlankTitle() {
		String output = """
				{ "overview": "흐름", "top_stories": [ { "title": " ", "summary": "s", "refs": ["A1"] } ], "more_headlines": [] }
				""";

		assertThat(DigestOutputReader.read(output))
			.isEqualTo(new OutputRead.Unreadable("$.top_stories[0].title: 비었다"));
	}

}
