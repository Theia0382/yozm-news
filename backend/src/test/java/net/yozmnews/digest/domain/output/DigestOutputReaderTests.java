package net.yozmnews.digest.domain.output;

import static net.yozmnews.digest.DigestInputs.event;
import static net.yozmnews.digest.DigestInputs.item;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import net.yozmnews.digest.DigestInputs;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.item.ItemKind;
import org.junit.jupiter.api.Test;

/**
 * 출력 원문 읽기 — 코드 펜스 · JSON 하나 · 필수 · 타입 · 상한과 넘을 때의 처분, 항목 칸의 없는 키 · 모르는 키. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: digest-R4 · digest-R5
 * <p>
 * 항목은 I1 · I2(주요 뉴스) · I3 · I4(헤드라인)이다. 원문과 기대값은 손으로 쓴 리터럴이다. 길이 경계는 한글 · 보충 평면 문자(UTF-16 두 칸)로
 * 코드 포인트 수를 센다.
 */
class DigestOutputReaderTests {

	private static final OutputShape.Obj ROOT = DigestOutputSchema.root(CandidateSet.of(DigestInputs.items(
			item(ItemKind.TOP_STORY, event(1, SelectionReason.SUBSCRIBED, 11)),
			item(ItemKind.TOP_STORY, event(2, SelectionReason.SUBSCRIBED, 12)),
			item(ItemKind.HEADLINE, event(3, SelectionReason.SUBSCRIBED, 13)),
			item(ItemKind.HEADLINE, event(4, SelectionReason.SUBSCRIBED, 14)))));

	private static final String NORMAL = """
			{
			  "overview": " 오늘의 흐름 ",
			  "top_stories": {
			    "I1": { "title": "제목 하나", "summary": "요약 하나" },
			    "I2": { "title": "제목 둘", "summary": "요약 둘" }
			  },
			  "headlines": {
			    "I3": { "title": " 헤드라인 셋 ", "note": "한 줄" },
			    "I4": { "title": "헤드라인 넷" }
			  }
			}
			""";

	/** 보충 평면 문자 하나 — UTF-16 두 칸, 코드 포인트 하나 */
	private static final String WIDE = "𝐀";

	@Test
	void readsANormalOutputAndStripsValues() {
		assertThat(read(NORMAL)).isEqualTo(new OutputRead.Read(new DigestDraft("오늘의 흐름",
				stories("I1", new StoryDraft("제목 하나", "요약 하나"), "I2", new StoryDraft("제목 둘", "요약 둘")),
				headlines("I3", new HeadlineDraft("헤드라인 셋", "한 줄"), "I4", new HeadlineDraft("헤드라인 넷", null))), 0));
	}

	@Test
	void readsAnOutputWithAMissingOrNullItemWithoutThatItem() {
		String missing = """
				{ "overview": "흐름", "top_stories": { "I2": { "title": "t2", "summary": "s2" } },
				  "headlines": { "I3": null } }
				""";

		// 빠진 항목은 출력 전체를 읽지 못한 것이 아니다 — I1 · I3 · I4 가 없는 초안이고 상한으로 버린 수는 0
		assertThat(read(missing)).isEqualTo(new OutputRead.Read(
				new DigestDraft("흐름", stories("I2", new StoryDraft("t2", "s2")), Map.of()), 0));
	}

	@Test
	void ignoresUnknownKeysAndKeysOfTheOtherField() {
		String extra = """
				{ "overview": "흐름", "mood": "맑음",
				  "top_stories": { "I1": { "title": "t1", "summary": "s1", "url": "x" }, "I3": { "title": "t3", "summary": "s3" },
				                   "Z9": { "title": "t", "summary": "s" } },
				  "headlines": { "I1": { "title": "h1" } } }
				""";

		assertThat(read(extra)).isEqualTo(new OutputRead.Read(
				new DigestDraft("흐름", stories("I1", new StoryDraft("t1", "s1")), Map.of()), 0));
	}

	@Test
	void unwrapsOneCodeFence() {
		assertThat(read("```json\n" + NORMAL + "```")).isInstanceOf(OutputRead.Read.class);
		assertThat(read("  ```\n" + NORMAL + "```\n")).isInstanceOf(OutputRead.Read.class);
	}

	@Test
	void doesNotUnwrapTwoCodeFences() {
		assertThat(read("```json\n```json\n" + NORMAL + "```\n```")).isEqualTo(new OutputRead.Unreadable("JSON 이 아니다"));
	}

	@Test
	void rejectsSomethingThatIsNotOneJsonObject() {
		assertThat(read("오늘은 조용했다.")).isEqualTo(new OutputRead.Unreadable("JSON 이 아니다"));
		assertThat(read("")).isEqualTo(new OutputRead.Unreadable("JSON 이 아니다"));
		assertThat(read("[" + NORMAL + "]")).isEqualTo(new OutputRead.Unreadable("$: 객체가 아니다"));
		assertThat(read(NORMAL + " {}")).isEqualTo(new OutputRead.Unreadable("JSON 이 아니다"));
	}

	@Test
	void rejectsADuplicateKey() {
		String twice = """
				{ "overview": "하나", "overview": "둘", "top_stories": {}, "headlines": {} }
				""";

		assertThat(read(twice)).isEqualTo(new OutputRead.Unreadable("JSON 이 아니다"));
	}

	@Test
	void rejectsAMissingOrBlankOverviewAndAMissingField() {
		assertThat(read("""
				{ "top_stories": {}, "headlines": {} }
				""")).isEqualTo(new OutputRead.Unreadable("$.overview: 없다"));
		assertThat(read("""
				{ "overview": "  ", "top_stories": {}, "headlines": {} }
				""")).isEqualTo(new OutputRead.Unreadable("$.overview: 비었다"));
		assertThat(read("""
				{ "overview": null, "top_stories": {}, "headlines": {} }
				""")).isEqualTo(new OutputRead.Unreadable("$.overview: null 이다"));
		// 옛 칸 이름은 모르는 속성이라 헤드라인 칸이 없다
		assertThat(read("""
				{ "overview": "흐름", "top_stories": {}, "more_headlines": {} }
				""")).isEqualTo(new OutputRead.Unreadable("$.headlines: 없다"));
	}

	@Test
	void rejectsAWrongTypeWithoutConverting() {
		assertThat(read("""
				{ "overview": "흐름", "top_stories": [], "headlines": {} }
				""")).isEqualTo(new OutputRead.Unreadable("$.top_stories: 객체가 아니다"));
		assertThat(read("""
				{ "overview": "흐름", "top_stories": { "I1": "t" }, "headlines": {} }
				""")).isEqualTo(new OutputRead.Unreadable("$.top_stories.I1: 객체가 아니다"));
		assertThat(read("""
				{ "overview": "흐름", "top_stories": { "I1": { "title": 1, "summary": "s" } }, "headlines": {} }
				""")).isEqualTo(new OutputRead.Unreadable("$.top_stories.I1.title: 문자열이 아니다"));
	}

	@Test
	void takesANullOrBlankNoteAsNone() {
		String notes = """
				{ "overview": "흐름", "top_stories": {},
				  "headlines": { "I3": { "title": "t", "note": null }, "I4": { "title": "t", "note": " " } } }
				""";

		assertThat(read(notes)).isEqualTo(new OutputRead.Read(new DigestDraft("흐름", Map.of(),
				headlines("I3", new HeadlineDraft("t", null), "I4", new HeadlineDraft("t", null))), 0));
	}

	@Test
	void aMissingOrBlankTitleOrSummaryInAnItemFailsTheWholeOutput() {
		assertThat(read("""
				{ "overview": "흐름", "top_stories": {}, "headlines": { "I3": {} } }
				""")).isEqualTo(new OutputRead.Unreadable("$.headlines.I3.title: 없다"));
		assertThat(read("""
				{ "overview": "흐름", "top_stories": {}, "headlines": { "I3": { "title": " " } } }
				""")).isEqualTo(new OutputRead.Unreadable("$.headlines.I3.title: 비었다"));
		assertThat(read("""
				{ "overview": "흐름", "top_stories": { "I1": { "title": "t", "summary": " " } }, "headlines": {} }
				""")).isEqualTo(new OutputRead.Unreadable("$.top_stories.I1.summary: 비었다"));
	}

	@Test
	void dropsOnlyTheItemWhoseTitleIsOverTheLimit() {
		String atLimit = "가".repeat(199) + WIDE;
		String overLimit = WIDE.repeat(201);
		String output = """
				{ "overview": "흐름", "top_stories": {
				    "I1": { "title": "%s", "summary": "s" },
				    "I2": { "title": "%s", "summary": "s" }
				  }, "headlines": {
				    "I3": { "title": "%s" },
				    "I4": { "title": "%s" }
				  } }
				""".formatted(atLimit, overLimit, overLimit, atLimit);

		assertThat(read(output)).isEqualTo(new OutputRead.Read(new DigestDraft("흐름",
				stories("I1", new StoryDraft(atLimit, "s")), headlines("I4", new HeadlineDraft(atLimit, null))), 2));
	}

	@Test
	void failsOnAMissingPropertyEvenBesideAnOverLimitTitle() {
		String missingSummary = """
				{ "overview": "흐름", "top_stories": { "I1": { "title": "%s" } }, "headlines": {} }
				""".formatted("가".repeat(201));
		String withSummary = """
				{ "overview": "흐름", "top_stories": { "I1": { "title": "%s", "summary": "s" } }, "headlines": {} }
				""".formatted("가".repeat(201));

		assertThat(read(withSummary)).isEqualTo(new OutputRead.Read(new DigestDraft("흐름", Map.of(), Map.of()), 1));
		assertThat(read(missingSummary)).isEqualTo(new OutputRead.Unreadable("$.top_stories.I1.summary: 없다"));
	}

	@Test
	void countsADroppedHeadlineOnceEvenWhenItsNoteIsAlsoOverTheLimit() {
		String output = """
				{ "overview": "흐름", "top_stories": {},
				  "headlines": { "I3": { "title": "%s", "note": "%s" }, "I4": { "title": "t" } } }
				""".formatted("가".repeat(201), "가".repeat(301));

		assertThat(read(output)).isEqualTo(
				new OutputRead.Read(new DigestDraft("흐름", Map.of(), headlines("I4", new HeadlineDraft("t", null))), 1));
	}

	@Test
	void dropsTheStoryWhoseSummaryIsOverTheLimit() {
		String output = """
				{ "overview": "흐름", "top_stories": { "I1": { "title": "t", "summary": "%s" } }, "headlines": {} }
				""".formatted("가".repeat(2_001));

		assertThat(read(output)).isEqualTo(new OutputRead.Read(new DigestDraft("흐름", Map.of(), Map.of()), 1));
	}

	@Test
	void failsWhenTheOverviewIsOverTheLimit() {
		String atLimit = """
				{ "overview": "%s", "top_stories": {}, "headlines": {} }
				""".formatted(WIDE.repeat(1_000));
		String overLimit = """
				{ "overview": "%s", "top_stories": {}, "headlines": {} }
				""".formatted(WIDE.repeat(1_001));

		assertThat(read(atLimit)).isInstanceOf(OutputRead.Read.class);
		assertThat(read(overLimit)).isEqualTo(new OutputRead.Unreadable("$.overview: 1000 코드 포인트를 넘는다"));
	}

	@Test
	void dropsOnlyANoteThatIsOverTheLimit() {
		String output = """
				{ "overview": "흐름", "top_stories": {},
				  "headlines": { "I3": { "title": "t", "note": "%s" }, "I4": { "title": "t", "note": "%s" } } }
				""".formatted("가".repeat(301), "가".repeat(300));

		assertThat(read(output)).isEqualTo(new OutputRead.Read(new DigestDraft("흐름", Map.of(),
				headlines("I3", new HeadlineDraft("t", null), "I4", new HeadlineDraft("t", "가".repeat(300)))), 1));
	}

	@Test
	void aFieldWithoutItemsIsAnEmptyObjectThatIgnoresItsKeys() {
		OutputShape.Obj topOnly = DigestOutputSchema.root(CandidateSet
			.of(DigestInputs.items(item(ItemKind.TOP_STORY, event(1, SelectionReason.SUBSCRIBED, 11)))));
		String output = """
				{ "overview": "흐름", "top_stories": { "I1": { "title": "t", "summary": "s" } },
				  "headlines": { "I9": { "title": "h" } } }
				""";

		assertThat(DigestOutputReader.read(output, topOnly)).isEqualTo(
				new OutputRead.Read(new DigestDraft("흐름", stories("I1", new StoryDraft("t", "s")), Map.of()), 0));
	}

	private static OutputRead read(String text) {
		return DigestOutputReader.read(text, ROOT);
	}

	private static Map<String, StoryDraft> stories(Object... aliasAndDraft) {
		Map<String, StoryDraft> map = new LinkedHashMap<>();
		for (int i = 0; i < aliasAndDraft.length; i += 2) {
			map.put((String) aliasAndDraft[i], (StoryDraft) aliasAndDraft[i + 1]);
		}
		return map;
	}

	private static Map<String, HeadlineDraft> headlines(Object... aliasAndDraft) {
		Map<String, HeadlineDraft> map = new LinkedHashMap<>();
		for (int i = 0; i < aliasAndDraft.length; i += 2) {
			map.put((String) aliasAndDraft[i], (HeadlineDraft) aliasAndDraft[i + 1]);
		}
		return map;
	}

}
