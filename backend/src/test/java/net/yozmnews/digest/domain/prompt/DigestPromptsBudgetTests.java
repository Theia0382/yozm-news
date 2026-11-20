package net.yozmnews.digest.domain.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.input.CandidateArticle;
import net.yozmnews.digest.domain.input.DigestInput;
import net.yozmnews.digest.domain.input.DigestProfile;
import net.yozmnews.digest.domain.input.InputEvent;
import net.yozmnews.digest.domain.input.InputItem;
import net.yozmnews.digest.domain.item.ItemKind;
import net.yozmnews.digest.domain.selection.CandidateCost;
import net.yozmnews.event.EventId;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.user.SummaryLength;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 프롬프트가 후보 선별의 토큰 어림 안에 드는가(ADR-0210) — 지시 + 항목이 없는 출력 스키마는 지시 예약 안, 프로필(고정 틀 포함) · 기사
 * 줄은 각 어림 안, 사건 · 항목의 괄호 · 항목 별칭 · 칸 이름과 출력 스키마에서 그 항목의 칸 서술은 사건마다 사건 몫 안. 긴 이름 ·
 * 이스케이프가 많은 글 · 가장 긴 시간대 ID · 가장 긴 항목 별칭으로 자르기와 몫을 걸고, 짧은 줄은 자르지 않는 대조를 둔다. 어림은
 * {@link CandidateCost} 의 공개 함수로 센다 — 비율을 다시 적지 않는다.
 */
class DigestPromptsBudgetTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final Instant PUBLISHED = Instant.parse("2026-11-17T03:30:00Z");

	/** 따옴표 · 역빗금이 많아 JSON 이스케이프로 길어지는 글 */
	private static final String ESCAPY = "\"\\".repeat(150);

	@Test
	void theInstructionsAndTheSchemaFitTheReserveInEveryCombination() {
		for (SummaryLength length : SummaryLength.values()) {
			for (boolean structured : new boolean[] { true, false }) {
				DigestInput empty = input(profile(length, "", "Asia/Seoul"), List.of());
				DigestPrompt first = DigestPrompts.compose(empty, CandidateSet.of(empty),
						new LlmModel(Provider.OPENAI, "gpt-test", 32_000, structured));
				DigestPrompt retry = DigestPrompts.retry(first, "가".repeat(PromptRules.MAX_DIAGNOSIS + 50));
				for (DigestPrompt prompt : List.of(first, retry)) {
					// 스키마는 강제 인자로 가도 공급자가 입력으로 셀 수 있다 — 지시에 실렸든 아니든 한 번 센다
					int schema = structured ? codePoints(prompt.outputSchema()) : 0;
					assertThat(CandidateCost.tokens(codePoints(prompt.instructions()) + schema))
						.as("%s · 구조화 %s · 다시 묻기 %s", length, structured, prompt == retry)
						.isLessThanOrEqualTo(CandidateCost.INSTRUCTION_RESERVE);
				}
			}
		}
	}

	@Test
	void aLongArticleLineIsCutWithinItsEstimateAndAShortOneIsNot() throws Exception {
		List<String> manySources = List.of("출처 이름이 아주 긴 언론사 하나", "출처 이름이 아주 긴 언론사 둘", "출처 이름이 아주 긴 언론사 셋");
		List<String> manyTopics = List.of("대통령실·행정부", "국회·정당", "북한", "외교·국방", "증권·시장");
		List<String> manyRegions = List.of("국내", "미국", "중국", "일본", "북한·한반도");
		CandidateArticle crowded = new CandidateArticle(new DigestCandidate(new ArticleId(1), SelectionReason.SUBSCRIBED),
				"제목", "요약문", manySources, PUBLISHED, manyTopics, manyRegions);
		CandidateArticle escapy = new CandidateArticle(new DigestCandidate(new ArticleId(2), SelectionReason.SUBSCRIBED),
				"제목", ESCAPY, List.of("출처"), PUBLISHED, List.of("정치"), List.of("국내"));
		CandidateArticle plain = new CandidateArticle(new DigestCandidate(new ArticleId(3), SelectionReason.SUBSCRIBED),
				"짧은 제목", "짧은 요약", List.of("연합뉴스"), PUBLISHED, List.of("정치"), List.of("국내"));
		List<String> sixRegions = List.of("국내", "미국", "중국", "일본", "북한·한반도", "러시아");
		CandidateArticle regional = new CandidateArticle(
				new DigestCandidate(new ArticleId(4), SelectionReason.SUBSCRIBED), "제목", "요약문", List.of("연합뉴스"),
				PUBLISHED, List.of("정치", "국회·정당"), sixRegions);
		DigestInput input = input(profile(SummaryLength.NORMAL, "", "Asia/Seoul"),
				List.of(new InputItem(ItemKind.TOP_STORY, List.of(event(10, crowded, escapy, plain, regional)))));

		JsonNode lines = JSON.readTree(compose(input).userContent()).get("items").get(0).get(2).get(0);

		assertThat(tokens(lines.get(0))).isLessThanOrEqualTo(CandidateCost.candidate(2, 3));
		assertThat(tokens(lines.get(1))).isLessThanOrEqualTo(CandidateCost.candidate(2, 300));
		// 넘친 줄(몫 2 + 3 + 80 = 85)은 지역을 모두, 그다음 주제를 모두, 그다음 출처의 꼬리 하나를 뺐다 — 남은 줄은 쉼표까지 78.
		// 요약문은 그대로다
		assertThat(lines.get(0)).isEqualTo(JSON.readTree("""
				["출처 이름이 아주 긴 언론사 하나, 출처 이름이 아주 긴 언론사 둘","2026-11-17 12:30",[],[],"제목","요약문"]"""));
		// 이스케이프로 넘친 줄은 이름을 다 뺀 뒤 요약문의 꼬리를 잘랐다
		assertThat(lines.get(1).get(0).asString()).isEmpty();
		assertThat(lines.get(1).get(2)).isEmpty();
		assertThat(lines.get(1).get(3)).isEmpty();
		assertThat(lines.get(1).get(5).asString()).isNotEmpty().hasSizeLessThan(ESCAPY.length());
		assertThat(lines.get(1).get(4).asString()).isEqualTo("제목");
		// 대조 — 짧은 줄은 자르지 않는다
		assertThat(lines.get(2)).isEqualTo(JSON.readTree("""
				["연합뉴스","2026-11-17 12:30",["정치"],["국내"],"짧은 제목","짧은 요약"]"""));
		// 조금 넘친 줄(쉼표까지 91 > 85)은 지역의 꼬리 하나만 뺀다 — 주제를 먼저 뺐다면 주제 "국회·정당"이 빠졌을 것이다
		assertThat(lines.get(3)).isEqualTo(JSON.readTree("""
				["연합뉴스","2026-11-17 12:30",["정치","국회·정당"],["국내","미국","중국","일본","북한·한반도"],"제목","요약문"]"""));
	}

	@Test
	void theProfileWithTheLongestTimezoneAndAnEscapingInterestFitsItsEstimate() throws Exception {
		String longestZone = ZoneId.getAvailableZoneIds()
			.stream()
			.max(Comparator.comparingInt(String::length))
			.orElseThrow();
		String interest = ESCAPY + "가".repeat(200);
		List<String> topics = List.of("이름이 아주 아주 아주 긴 주제 하나 둘 셋 넷", "정치");
		List<String> regions = List.of("미국");
		DigestProfile profile = new DigestProfile(topics, regions, interest, ZoneId.of(longestZone), SummaryLength.NORMAL,
				ReasoningEffort.MEDIUM);

		JsonNode root = JSON.readTree(compose(input(profile, List.of())).userContent());

		int allowed = CandidateCost.tokens(codePoints(interest)
				+ (topics.size() + regions.size()) * CandidateCost.PROFILE_TAG_COST + CandidateCost.PROFILE_OVERHEAD);
		assertThat(CandidateCost.tokens(codePoints(JSON.writeValueAsString(root)))).isLessThanOrEqualTo(allowed);
		assertThat(root.get("profile").get("timezone").asString()).isEqualTo(longestZone);
		// 긴 이름은 이름 몫까지 잘리고 짧은 이름은 그대로
		assertThat(root.get("profile").get("topics").get(0).asString()).startsWith("이름이 아주")
			.hasSizeLessThan(topics.get(0).length());
		assertThat(root.get("profile").get("topics").get(1).asString()).isEqualTo("정치");
	}

	@Test
	void theProfileWithoutInterestOrNamesKeepsItsFrame() throws Exception {
		DigestProfile empty = new DigestProfile(List.of(), List.of(), "", ZoneId.of("UTC"), SummaryLength.SHORT,
				ReasoningEffort.LOW);

		String content = compose(input(empty, List.of())).userContent();

		assertThat(JSON.readTree(content)).isEqualTo(JSON.readTree("""
				{"profile":{"topics":[],"excludedRegions":[],"interest":"","timezone":"UTC"},"items":[]}"""));
		assertThat(CandidateCost.tokens(codePoints(content))).isLessThanOrEqualTo(CandidateCost.PROFILE_OVERHEAD);
	}

	@Test
	void everyItemFitsTheShareOfItsEventsWithTheLongestItemAlias() throws Exception {
		// 항목 150 개 — 마지막 항목의 별칭은 가장 긴 I150 이고 칸 이름은 긴 쪽(top_stories)이다. 항목 149 는 사건 셋 · 기사 둘씩
		List<InputItem> items = new ArrayList<>();
		long article = 1;
		for (int i = 1; i <= 150; i++) {
			int events = (i == 149) ? 3 : 1;
			List<InputEvent> ofItem = new ArrayList<>();
			for (int e = 0; e < events; e++) {
				int articles = (i == 149) ? 2 : 1;
				CandidateArticle[] lines = new CandidateArticle[articles];
				for (int a = 0; a < articles; a++) {
					lines[a] = new CandidateArticle(new DigestCandidate(new ArticleId(article++), SelectionReason.SUBSCRIBED),
							"제목", "", List.of(), PUBLISHED, List.of(), List.of());
				}
				ofItem.add(event(i * 10L + e, lines));
			}
			items.add(new InputItem(ItemKind.TOP_STORY, ofItem));
		}
		DigestInput input = input(profile(SummaryLength.NORMAL, "", "Asia/Seoul"), items);

		JsonNode root = JSON.readTree(compose(input).userContent());
		JsonNode last = root.get("items").get(149);
		JsonNode many = root.get("items").get(148);

		assertThat(last.get(0).asString()).isEqualTo("I150");
		int line = CandidateCost.candidate(2, 0);
		assertThat(tokens(last)).isLessThanOrEqualTo(CandidateCost.event(List.of(line)));
		assertThat(tokens(many)).isLessThanOrEqualTo(3 * CandidateCost.event(List.of(line, line)));
		// 사용자 구획 전체가 프로필 몫(구독 행 하나) + 사건마다의 몫 안이다 — 사건 하나짜리 항목 149 개와 사건 셋짜리 항목 하나
		int allowed = CandidateCost.tokens(CandidateCost.PROFILE_TAG_COST + CandidateCost.PROFILE_OVERHEAD)
				+ 149 * CandidateCost.event(List.of(line)) + 3 * CandidateCost.event(List.of(line, line));
		assertThat(CandidateCost.tokens(codePoints(compose(input).userContent()))).isLessThanOrEqualTo(allowed);
	}

	@Test
	void anItemsShareOfTheUserSegmentAndOfTheOutputSchemaFitsItsEventEstimate() throws Exception {
		// 사건 하나 · 기사 하나짜리 항목 150 개 — 마지막 항목의 별칭은 가장 긴 I150 이다. 출력 서술은 항목마다 칸 하나를 더하고, 스키마는 구조화
		// 출력이어도 공급자가 입력으로 셀 수 있다. 항목 하나가 사용자 구획과 출력 서술에 더하는 몫이 함께 그 사건의 어림 안이어야 한다
		for (ItemKind kind : ItemKind.values()) {
			DigestInput all = singles(150, kind);
			DigestInput lessOne = singles(149, kind);
			DigestPrompt prompt = compose(all);
			JsonNode last = JSON.readTree(prompt.userContent()).get("items").get(149);
			int schemaShare = codePoints(prompt.outputSchema()) - codePoints(compose(lessOne).outputSchema());

			assertThat(last.get(0).asString()).isEqualTo("I150");
			assertThat(tokens(last) + CandidateCost.tokens(schemaShare)).as("%s", kind)
				.isLessThanOrEqualTo(CandidateCost.event(List.of(CandidateCost.candidate(2, 0))));
		}
	}

	/** 사건 하나 · 기사 하나(제목 "제목", 요약문 없음)짜리 항목 {@code count} 개 */
	private static DigestInput singles(int count, ItemKind kind) {
		List<InputItem> items = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			CandidateArticle line = new CandidateArticle(new DigestCandidate(new ArticleId(i), SelectionReason.SUBSCRIBED),
					"제목", "", List.of(), PUBLISHED, List.of(), List.of());
			items.add(new InputItem(kind, List.of(event(i, line))));
		}
		return input(profile(SummaryLength.NORMAL, "", "Asia/Seoul"), items);
	}

	private static DigestPrompt compose(DigestInput input) {
		return DigestPrompts.compose(input, CandidateSet.of(input), new LlmModel(Provider.OPENAI, "gpt-test", 32_000, true));
	}

	private static DigestInput input(DigestProfile profile, List<InputItem> items) {
		return new DigestInput(profile, items);
	}

	private static InputEvent event(long id, CandidateArticle... articles) {
		return new InputEvent(new EventId(id), SelectionReason.SUBSCRIBED, List.of(articles));
	}

	private static DigestProfile profile(SummaryLength length, String interest, String zone) {
		return new DigestProfile(List.of("정치"), List.of(), interest, ZoneId.of(zone), length, ReasoningEffort.MEDIUM);
	}

	/** 배열 줄 하나와 그 뒤 쉼표의 토큰 어림 */
	private static int tokens(JsonNode line) {
		return CandidateCost.tokens(codePoints(JSON.writeValueAsString(line)) + 1);
	}

	private static int codePoints(String text) {
		return text.codePointCount(0, text.length());
	}

}
