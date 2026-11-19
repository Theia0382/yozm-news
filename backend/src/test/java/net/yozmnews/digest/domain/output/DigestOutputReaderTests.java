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
			    { "ref": "I1", "title": "제목 하나", "summary": "요약 하나" }
			  ],
			  "headlines": [
			    { "ref": "I2", "title": " 헤드라인 둘 ", "note": "한 줄" },
			    { "ref": "I3", "title": "헤드라인 셋" }
			  ]
			}
			""";

	/** 보충 평면 문자 하나 — UTF-16 두 칸, 코드 포인트 하나 */
	private static final String WIDE = "𝐀";

	@Test
	void readsANormalOutputAndStripsValues() {
		OutputRead read = DigestOutputReader.read(NORMAL);

		assertThat(read).isEqualTo(new OutputRead.Read(new DigestDraft("오늘의 흐름",
				List.of(new StoryDraft("I1", "제목 하나", "요약 하나")),
				List.of(new HeadlineDraft("I2", "헤드라인 둘", "한 줄"), new HeadlineDraft("I3", "헤드라인 셋", null))), 0));
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
				{ "overview": "하나", "overview": "둘", "top_stories": [], "headlines": [] }
				""";

		assertThat(DigestOutputReader.read(twice)).isEqualTo(new OutputRead.Unreadable("JSON 이 아니다"));
	}

	@Test
	void rejectsAMissingOrBlankOverviewAndAMissingList() {
		assertThat(DigestOutputReader.read("""
				{ "top_stories": [], "headlines": [] }
				""")).isEqualTo(new OutputRead.Unreadable("$.overview: 없다"));
		assertThat(DigestOutputReader.read("""
				{ "overview": "  ", "top_stories": [], "headlines": [] }
				""")).isEqualTo(new OutputRead.Unreadable("$.overview: 비었다"));
		assertThat(DigestOutputReader.read("""
				{ "overview": null, "top_stories": [], "headlines": [] }
				""")).isEqualTo(new OutputRead.Unreadable("$.overview: null 이다"));
		// 옛 칸 이름은 모르는 속성이라 헤드라인 목록이 없다
		assertThat(DigestOutputReader.read("""
				{ "overview": "흐름", "top_stories": [], "more_headlines": [] }
				""")).isEqualTo(new OutputRead.Unreadable("$.headlines: 없다"));
	}

	@Test
	void rejectsAWrongTypeWithoutConverting() {
		String numberRef = """
				{ "overview": "흐름", "top_stories": [ { "ref": 1, "title": "t", "summary": "s" } ], "headlines": [] }
				""";
		String stringRef = """
				{ "overview": "흐름", "top_stories": [ { "ref": "I1", "title": "t", "summary": "s" } ], "headlines": [] }
				""";

		assertThat(DigestOutputReader.read(numberRef))
			.isEqualTo(new OutputRead.Unreadable("$.top_stories[0].ref: 문자열이 아니다"));
		assertThat(DigestOutputReader.read(stringRef)).isInstanceOf(OutputRead.Read.class);
		assertThat(DigestOutputReader.read("""
				{ "overview": "흐름", "top_stories": {}, "headlines": [] }
				""")).isEqualTo(new OutputRead.Unreadable("$.top_stories: 배열이 아니다"));
	}

	@Test
	void ignoresUnknownProperties() {
		String extra = """
				{ "overview": "흐름", "mood": "맑음", "top_stories": [],
				  "headlines": [ { "ref": "I1", "title": "t", "url": "x", "refs": ["A1"] } ] }
				""";

		assertThat(DigestOutputReader.read(extra)).isEqualTo(new OutputRead.Read(
				new DigestDraft("흐름", List.of(), List.of(new HeadlineDraft("I1", "t", null))), 0));
	}

	@Test
	void takesANullOrBlankNoteAsNone() {
		String notes = """
				{ "overview": "흐름", "top_stories": [],
				  "headlines": [ { "ref": "I1", "title": "t", "note": null }, { "ref": "I2", "title": "t", "note": " " } ] }
				""";

		assertThat(DigestOutputReader.read(notes)).isEqualTo(new OutputRead.Read(new DigestDraft("흐름", List.of(),
				List.of(new HeadlineDraft("I1", "t", null), new HeadlineDraft("I2", "t", null))), 0));
	}

	@Test
	void aHeadlineNeedsATitle() {
		assertThat(DigestOutputReader.read("""
				{ "overview": "흐름", "top_stories": [], "headlines": [ { "ref": "I1" } ] }
				""")).isEqualTo(new OutputRead.Unreadable("$.headlines[0].title: 없다"));
		assertThat(DigestOutputReader.read("""
				{ "overview": "흐름", "top_stories": [], "headlines": [ { "ref": "I1", "title": " " } ] }
				""")).isEqualTo(new OutputRead.Unreadable("$.headlines[0].title: 비었다"));
	}

	@Test
	void dropsOnlyTheElementWhoseTitleIsOverTheLimit() {
		String atLimit = "가".repeat(199) + WIDE;
		String overLimit = WIDE.repeat(201);
		String output = """
				{ "overview": "흐름", "top_stories": [
				    { "ref": "I1", "title": "%s", "summary": "s" },
				    { "ref": "I2", "title": "%s", "summary": "s" }
				  ], "headlines": [
				    { "ref": "I3", "title": "%s" },
				    { "ref": "I4", "title": "%s" }
				  ] }
				""".formatted(atLimit, overLimit, overLimit, atLimit);

		assertThat(DigestOutputReader.read(output)).isEqualTo(new OutputRead.Read(new DigestDraft("흐름",
				List.of(new StoryDraft("I1", atLimit, "s")), List.of(new HeadlineDraft("I4", atLimit, null))), 2));
	}

	@Test
	void failsOnAMissingPropertyEvenBesideAnOverLimitTitle() {
		String missingSummary = """
				{ "overview": "흐름", "top_stories": [ { "ref": "I1", "title": "%s" } ], "headlines": [] }
				""".formatted("가".repeat(201));
		String withSummary = """
				{ "overview": "흐름", "top_stories": [ { "ref": "I1", "title": "%s", "summary": "s" } ], "headlines": [] }
				""".formatted("가".repeat(201));

		assertThat(DigestOutputReader.read(withSummary))
			.isEqualTo(new OutputRead.Read(new DigestDraft("흐름", List.of(), List.of()), 1));
		assertThat(DigestOutputReader.read(missingSummary))
			.isEqualTo(new OutputRead.Unreadable("$.top_stories[0].summary: 없다"));
	}

	@Test
	void countsADroppedHeadlineOnceEvenWhenItsNoteIsAlsoOverTheLimit() {
		String output = """
				{ "overview": "흐름", "top_stories": [],
				  "headlines": [ { "ref": "%s", "title": "t", "note": "%s" }, { "ref": "I2", "title": "t" } ] }
				""".formatted("I".repeat(17), "가".repeat(301));

		assertThat(DigestOutputReader.read(output)).isEqualTo(
				new OutputRead.Read(new DigestDraft("흐름", List.of(), List.of(new HeadlineDraft("I2", "t", null))), 1));
	}

	@Test
	void dropsTheStoryWhoseSummaryIsOverTheLimit() {
		String output = """
				{ "overview": "흐름", "top_stories": [ { "ref": "I1", "title": "t", "summary": "%s" } ], "headlines": [] }
				""".formatted("가".repeat(2_001));

		assertThat(DigestOutputReader.read(output))
			.isEqualTo(new OutputRead.Read(new DigestDraft("흐름", List.of(), List.of()), 1));
	}

	@Test
	void failsWhenTheOverviewIsOverTheLimit() {
		String atLimit = """
				{ "overview": "%s", "top_stories": [], "headlines": [] }
				""".formatted(WIDE.repeat(1_000));
		String overLimit = """
				{ "overview": "%s", "top_stories": [], "headlines": [] }
				""".formatted(WIDE.repeat(1_001));

		assertThat(DigestOutputReader.read(atLimit)).isInstanceOf(OutputRead.Read.class);
		assertThat(DigestOutputReader.read(overLimit))
			.isEqualTo(new OutputRead.Unreadable("$.overview: 1000 코드 포인트를 넘는다"));
	}

	@Test
	void dropsOnlyANoteThatIsOverTheLimit() {
		String output = """
				{ "overview": "흐름", "top_stories": [],
				  "headlines": [ { "ref": "I1", "title": "t", "note": "%s" }, { "ref": "I2", "title": "t", "note": "%s" } ] }
				""".formatted("가".repeat(301), "가".repeat(300));

		assertThat(DigestOutputReader.read(output)).isEqualTo(new OutputRead.Read(new DigestDraft("흐름", List.of(),
				List.of(new HeadlineDraft("I1", "t", null), new HeadlineDraft("I2", "t", "가".repeat(300)))), 1));
	}

	@Test
	void readsOnlyTheFirstTwentyTopStories() {
		StringBuilder stories = new StringBuilder();
		for (int index = 1; index <= 21; index++) {
			if (index > 1) {
				stories.append(',');
			}
			stories.append("{ \"ref\": \"I").append(index).append("\", \"title\": \"t").append(index)
				.append("\", \"summary\": \"s\" }");
		}
		String output = "{ \"overview\": \"흐름\", \"top_stories\": [" + stories + "], \"headlines\": [] }";

		OutputRead.Read result = (OutputRead.Read) DigestOutputReader.read(output);

		assertThat(result.overLimit()).isEqualTo(1);
		assertThat(result.draft().topStories()).hasSize(20);
		assertThat(result.draft().topStories().get(19).title()).isEqualTo("t20");
	}

	@Test
	void readsOnlyTheFirstHundredFiftyHeadlines() {
		StringBuilder headlines = new StringBuilder();
		for (int index = 1; index <= 151; index++) {
			if (index > 1) {
				headlines.append(',');
			}
			headlines.append("{ \"ref\": \"I").append(index).append("\", \"title\": \"h").append(index).append("\" }");
		}
		String output = "{ \"overview\": \"흐름\", \"top_stories\": [], \"headlines\": [" + headlines + "] }";

		OutputRead.Read result = (OutputRead.Read) DigestOutputReader.read(output);

		assertThat(result.overLimit()).isEqualTo(1);
		assertThat(result.draft().headlines()).hasSize(150);
		assertThat(result.draft().headlines().get(149).title()).isEqualTo("h150");
	}

	@Test
	void dropsAnElementWhoseAliasIsOverTheLimit() {
		String output = """
				{ "overview": "흐름", "top_stories": [ { "ref": "%s", "title": "t", "summary": "s" }, { "ref": "I1", "title": "t", "summary": "s" } ],
				  "headlines": [ { "ref": "%s", "title": "h" }, { "ref": "I2", "title": "h" } ] }
				""".formatted("I".repeat(17), "I".repeat(17));

		assertThat(DigestOutputReader.read(output)).isEqualTo(new OutputRead.Read(
				new DigestDraft("흐름", List.of(new StoryDraft("I1", "t", "s")), List.of(new HeadlineDraft("I2", "h", null))),
				2));
	}

	@Test
	void keepsAnEmptyAliasForTheItemCheck() {
		String output = """
				{ "overview": "흐름", "top_stories": [ { "ref": " ", "title": "t", "summary": "s" } ],
				  "headlines": [ { "ref": "", "title": "h" }, { "ref": "I2", "title": "h" } ] }
				""";

		assertThat(DigestOutputReader.read(output)).isEqualTo(new OutputRead.Read(
				new DigestDraft("흐름", List.of(new StoryDraft("", "t", "s")),
						List.of(new HeadlineDraft("", "h", null), new HeadlineDraft("I2", "h", null))),
				0));
	}

	@Test
	void stillFailsOnABlankStoryTitle() {
		String output = """
				{ "overview": "흐름", "top_stories": [ { "ref": "I1", "title": " ", "summary": "s" } ], "headlines": [] }
				""";

		assertThat(DigestOutputReader.read(output))
			.isEqualTo(new OutputRead.Unreadable("$.top_stories[0].title: 비었다"));
	}

}
