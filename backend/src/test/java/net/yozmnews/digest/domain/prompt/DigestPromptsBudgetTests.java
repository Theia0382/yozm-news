package net.yozmnews.digest.domain.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.candidate.SelectionReason;
import net.yozmnews.digest.domain.input.CandidateArticle;
import net.yozmnews.digest.domain.input.DigestInput;
import net.yozmnews.digest.domain.input.DigestProfile;
import net.yozmnews.digest.domain.input.StorylineGroup;
import net.yozmnews.digest.domain.selection.CandidateCost;
import net.yozmnews.digest.domain.selection.SelectionRules;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.user.SummaryLength;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 프롬프트가 후보 선별의 토큰 어림 안에 드는가(ADR-0210) — 지시 + 출력 스키마는 지시 예약 안, 프로필(고정 틀 포함) · 묶음 줄 · 후보 줄은
 * 각 어림 안. 긴 이름 · 이스케이프가 많은 글 · 가장 긴 시간대 ID 로 자르기를 걸고, 짧은 줄은 자르지 않는 대조를 둔다. 어림은
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
				DigestPrompt first = DigestPrompts.compose(input(profile(length, "", "Asia/Seoul"), List.of(), List.of()),
						CandidateSet.of(List.of()), new LlmModel(Provider.OPENAI, "gpt-test", 32_000, structured));
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
	void aLongCandidateLineIsCutWithinItsEstimateAndAShortOneIsNot() throws Exception {
		List<String> manySources = List.of("출처 이름이 아주 긴 언론사 하나", "출처 이름이 아주 긴 언론사 둘", "출처 이름이 아주 긴 언론사 셋");
		List<String> manyTopics = List.of("대통령실·행정부", "국회·정당", "북한", "외교·국방", "증권·시장");
		List<String> manyRegions = List.of("국내", "미국", "중국", "일본", "북한·한반도");
		CandidateArticle crowded = new CandidateArticle(new DigestCandidate(new ArticleId(1), SelectionReason.SUBSCRIBED),
				"제목", "요약문", manySources, PUBLISHED, manyTopics, manyRegions);
		CandidateArticle escapy = new CandidateArticle(new DigestCandidate(new ArticleId(2), SelectionReason.SUBSCRIBED),
				"제목", ESCAPY, List.of("출처"), PUBLISHED, List.of("정치"), List.of("국내"));
		CandidateArticle plain = new CandidateArticle(new DigestCandidate(new ArticleId(3), SelectionReason.SUBSCRIBED),
				"짧은 제목", "짧은 요약", List.of("연합뉴스"), PUBLISHED, List.of("정치"), List.of("국내"));
		CandidateArticle regional = new CandidateArticle(
				new DigestCandidate(new ArticleId(4), SelectionReason.SUBSCRIBED), "제목", "요약문", List.of("연합뉴스"),
				PUBLISHED, List.of("정치", "국회·정당"), manyRegions);
		DigestInput input = input(profile(SummaryLength.NORMAL, "", "Asia/Seoul"),
				List.of(crowded, escapy, plain, regional), List.of());

		JsonNode lines = JSON.readTree(compose(input).userContent()).get("candidates");

		assertThat(tokens(lines.get(0))).isLessThanOrEqualTo(CandidateCost.candidate(2, 3));
		assertThat(tokens(lines.get(1))).isLessThanOrEqualTo(CandidateCost.candidate(2, 300));
		// 넘친 줄(몫 2 + 3 + 80 = 85)은 지역을 모두, 그다음 주제를 모두, 그다음 출처의 꼬리 하나를 뺐다 — 남은 줄은 쉼표까지 83.
		// 요약문은 그대로다
		assertThat(lines.get(0)).isEqualTo(JSON.readTree("""
				["A1","출처 이름이 아주 긴 언론사 하나, 출처 이름이 아주 긴 언론사 둘","2026-11-17 12:30",[],[],"제목","요약문"]"""));
		// 이스케이프로 넘친 줄은 이름을 다 뺀 뒤 요약문의 꼬리를 잘랐다
		assertThat(lines.get(1).get(1).asString()).isEmpty();
		assertThat(lines.get(1).get(3)).isEmpty();
		assertThat(lines.get(1).get(4)).isEmpty();
		assertThat(lines.get(1).get(6).asString()).isNotEmpty().hasSizeLessThan(ESCAPY.length());
		assertThat(lines.get(1).get(5).asString()).isEqualTo("제목");
		// 대조 — 짧은 줄은 자르지 않는다
		assertThat(lines.get(2)).isEqualTo(JSON.readTree("""
				["A3","연합뉴스","2026-11-17 12:30",["정치"],["국내"],"짧은 제목","짧은 요약"]"""));
		// 조금 넘친 줄(쉼표까지 90 > 85)은 지역의 꼬리 하나만 뺀다 — 주제를 먼저 뺐다면 주제 "국회·정당"이 빠졌을 것이다
		assertThat(lines.get(3)).isEqualTo(JSON.readTree("""
				["A4","연합뉴스","2026-11-17 12:30",["정치","국회·정당"],["국내","미국","중국","일본"],"제목","요약문"]"""));
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

		JsonNode root = JSON.readTree(compose(input(profile, List.of(), List.of())).userContent());

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

		String content = compose(input(empty, List.of(), List.of())).userContent();

		assertThat(JSON.readTree(content)).isEqualTo(JSON.readTree("""
				{"profile":{"topics":[],"excludedRegions":[],"interest":"","timezone":"UTC"},"storylines":[],
				 "candidates":[]}"""));
		assertThat(CandidateCost.tokens(codePoints(content))).isLessThanOrEqualTo(CandidateCost.PROFILE_OVERHEAD);
	}

	@Test
	void aFullStorylineGroupWithAnEscapingTitleFitsItsShare() throws Exception {
		// 최대 별칭 길이의 후보 — A141 ~ A150 이 묶음의 기사가 되도록 앞에 구독 후보를 140 개 둔다
		List<CandidateArticle> candidates = new ArrayList<>();
		for (int i = 1; i <= 140; i++) {
			candidates.add(new CandidateArticle(new DigestCandidate(new ArticleId(i), SelectionReason.SUBSCRIBED), "제목",
					"", List.of(), PUBLISHED, List.of(), List.of()));
		}
		List<ArticleId> members = new ArrayList<>();
		for (int i = 141; i <= 140 + SelectionRules.BIG_EVENTS_PER_STORYLINE; i++) {
			candidates.add(new CandidateArticle(new DigestCandidate(new ArticleId(i), SelectionReason.MAJOR_STORYLINE),
					"제목", "", List.of(), PUBLISHED, List.of(), List.of()));
			members.add(new ArticleId(i));
		}
		String title = "분쟁 " + "\"".repeat(30);
		DigestInput input = input(profile(SummaryLength.NORMAL, "", "Asia/Seoul"), candidates,
				List.of(new StorylineGroup(title, members)));

		JsonNode group = JSON.readTree(compose(input).userContent()).get("storylines").get(0);

		assertThat(group.get(2)).hasSize(SelectionRules.BIG_EVENTS_PER_STORYLINE);
		assertThat(group.get(2).get(9).asString()).isEqualTo("A150");
		assertThat(tokens(group)).isLessThanOrEqualTo(CandidateCost.storylines(List.of(codePoints(title))));

		// 대조 — 이스케이프가 없는 이름은 자르지 않는다
		DigestInput plain = input(profile(SummaryLength.NORMAL, "", "Asia/Seoul"), candidates,
				List.of(new StorylineGroup("분쟁 흐름", members)));
		assertThat(JSON.readTree(compose(plain).userContent()).get("storylines").get(0).get(1).asString())
			.isEqualTo("분쟁 흐름");
	}

	private static DigestPrompt compose(DigestInput input) {
		return DigestPrompts.compose(input, CandidateSet.of(input.digestCandidates()),
				new LlmModel(Provider.OPENAI, "gpt-test", 32_000, true));
	}

	private static DigestInput input(DigestProfile profile, List<CandidateArticle> candidates,
			List<StorylineGroup> storylines) {
		return new DigestInput(profile, candidates, storylines);
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
