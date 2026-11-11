package net.yozmnews.evaluation.adapter.out.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.EvaluatorFailureKind;
import net.yozmnews.evaluation.FormatChoice;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.ImportanceScale;
import net.yozmnews.evaluation.TagQuestions;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.evaluation.domain.evaluator.CandidateEvent;
import net.yozmnews.evaluation.domain.evaluator.Evaluation;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;
import net.yozmnews.evaluation.domain.evaluator.EvaluationInput;
import net.yozmnews.evaluation.domain.evaluator.EvaluationResult;
import net.yozmnews.evaluation.domain.evaluator.FeedContext;
import net.yozmnews.evaluation.domain.evaluator.Membership;
import net.yozmnews.evaluation.domain.evaluator.MembershipInput;
import net.yozmnews.evaluation.domain.evaluator.MembershipWording;
import net.yozmnews.evaluation.domain.evaluator.RereadFailure;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 고정 규칙 평가기 — 결정성 · 채움 · 이름 일치 · 실패 표지 · 입력 토큰 · 원본 응답과 그 다시 읽기, 사건 소속 판정의 조각 겹침. 해시로 고른
 * 위치는 단언하지 않고 개수와 규칙이 정한 값을 본다.
 */
class FakeArticleEvaluatorTests {

	/** 고정 체계 — 대분류 2 × 중분류 2, 지역 2, 형식 3, 중요도 1 ~ 5. 지역 · 형식은 체계 순서와 키 순서가 다르다. */
	private static final TagQuestions QUESTIONS = new TagQuestions(
			List.of(new TopicCategory("politics", "정치", "정치", List.of(
					new AskedTag("politics.executive", "대통령실·행정부", "대통령실과 행정부", "대통령실과 행정부를 다루는가"),
					new AskedTag("politics.assembly", "국회·정당·선거", "국회와 정당", "국회와 정당을 다루는가"))),
					new TopicCategory("economy", "경제", "경제", List.of(
							new AskedTag("economy.finance", "금융", "금융", "금융을 다루는가"),
							new AskedTag("economy.realestate", "부동산", "부동산", "부동산을 다루는가")))),
			List.of(new AskedTag("world", "국제", "국외", "국외의 일인가"),
					new AskedTag("domestic", "국내", "국내", "국내의 일인가")),
			new FormatChoice("이 기사의 형식은", List.of(new FormatOption("straight", "스트레이트", "사실 보도", false),
					new FormatOption("opinion", "오피니언", "의견", true),
					new FormatOption("feature", "기획", "기획 기사", false))),
			new ImportanceScale("얼마나 중요한가", List.of(new ImportanceLevel(1, "사소"), new ImportanceLevel(2, "작음"),
					new ImportanceLevel(3, "보통"), new ImportanceLevel(4, "큼"), new ImportanceLevel(5, "매우 큼"))));

	private final FakeArticleEvaluator evaluator = new FakeArticleEvaluator();

	/** 고정 체계의 키를 모두 가진 Fake 원본 응답 리터럴 */
	private static final String RAW = """
			{"evaluator":"fake","modelVersion":"fake-rules-2",\
			"topics":{"economy.finance":0.05,"economy.realestate":0.11,"politics.assembly":0.9,"politics.executive":0.7},\
			"regions":{"domestic":0.9,"world":0.03},\
			"formats":{"feature":0.1,"opinion":0.1,"straight":0.8},\
			"importance":{"1":0.1,"2":0.1,"3":0.6,"4":0.1,"5":0.1}}""";

	@Test
	void rereadsItsOwnRawResponseForTheTagSet() {
		assertThat(this.evaluator.reread("fake", RAW, QUESTIONS)).isEqualTo(new Evaluation(
				Map.of("politics.executive", 0.7, "politics.assembly", 0.9, "economy.finance", 0.05,
						"economy.realestate", 0.11),
				Map.of("world", 0.03, "domestic", 0.9), Map.of("straight", 0.8, "opinion", 0.1, "feature", 0.1),
				Map.of(1, 0.1, 2, 0.1, 3, 0.6, 4, 0.1, 5, 0.1), "fake", "fake-rules-2", 0, RAW));
	}

	@Test
	void rereadingAnEvaluationGivesItsProbabilitiesBack() {
		Evaluation evaluation = evaluate(input("대통령 시정연설", "정치"));

		Evaluation reread = (Evaluation) this.evaluator.reread("fake", evaluation.rawResponse(), QUESTIONS);

		assertThat(reread.topics()).isEqualTo(evaluation.topics());
		assertThat(reread.regions()).isEqualTo(evaluation.regions());
		assertThat(reread.formats()).isEqualTo(evaluation.formats());
		assertThat(reread.importance()).isEqualTo(evaluation.importance());
	}

	@Test
	void doesNotRereadTheResponseOfAnotherEvaluator() {
		assertThat(this.evaluator.reread("jev", RAW, QUESTIONS)).isEqualTo(
				new RereadFailure(RereadFailure.Kind.OTHER_EVALUATOR, "Fake 가 남긴 원본 응답이 아니다 — 평가기 jev"));
	}

	@Test
	void aRawResponseWithoutAProbabilityOfTheTagSetIsUnreadable() {
		assertThat(this.evaluator.reread("fake", RAW.replace(",\"world\":0.03", ""), QUESTIONS)).isEqualTo(
				new RereadFailure(RereadFailure.Kind.UNREADABLE,
						"Fake 원본 응답을 읽을 수 없다 — 확률이 없거나 숫자가 아니다 — regions.world"));
		assertThat(this.evaluator.reread("fake", RAW.replace("\"5\":0.1", "\"5\":1.5"), QUESTIONS)).isEqualTo(
				new RereadFailure(RereadFailure.Kind.UNREADABLE, "Fake 원본 응답을 읽을 수 없다 — 확률이 0 ~ 1 이 아니다 — importance.5"));
		assertThat(this.evaluator.reread("fake", "[]", QUESTIONS))
			.isEqualTo(new RereadFailure(RereadFailure.Kind.UNREADABLE, "Fake 원본 응답을 읽을 수 없다 — JSON 객체가 아니다"));
	}

	@Test
	void givesTheSameResultForTheSameInput() {
		EvaluationInput input = input("날씨 맑음", "전체기사");

		assertThat(this.evaluator.evaluate(input, QUESTIONS)).isEqualTo(this.evaluator.evaluate(input, QUESTIONS))
			.isEqualTo(new FakeArticleEvaluator().evaluate(input, QUESTIONS));
	}

	@Test
	void givesADifferentRawResponseForADifferentTitle() {
		assertThat(evaluate(input("날씨 맑음", "전체기사")).rawResponse())
			.isNotEqualTo(evaluate(input("날씨 흐림", "전체기사")).rawResponse());
	}

	@Test
	void fillsEveryAskedTagAndNothingElse() {
		Evaluation evaluation = evaluate(input("날씨 맑음", "전체기사"));

		assertThat(evaluation.topics()).containsOnlyKeys("politics.executive", "politics.assembly", "economy.finance",
				"economy.realestate");
		assertThat(evaluation.regions()).containsOnlyKeys("world", "domestic");
		assertThat(evaluation.formats()).containsOnlyKeys("straight", "opinion", "feature");
		assertThat(evaluation.importance()).containsOnlyKeys(1, 2, 3, 4, 5);
		assertThat(evaluation.evaluator()).isEqualTo("fake");
		assertThat(evaluation.modelVersion()).isEqualTo("fake-rules-2");
	}

	@Test
	void picksOneSubtopicOfACategoryNamedInTheSection() {
		Evaluation evaluation = evaluate(input("대통령 시정연설", "정치"));

		assertThat(values(evaluation.topics(), "politics.executive", "politics.assembly")).filteredOn(v -> v == 0.8)
			.hasSize(1);
		assertThat(values(evaluation.topics(), "politics.executive", "politics.assembly")).filteredOn(v -> v != 0.8)
			.allSatisfy(v -> assertThat(v).isLessThan(0.12));
		assertThat(values(evaluation.topics(), "economy.finance", "economy.realestate"))
			.allSatisfy(v -> assertThat(v).isLessThan(0.12));
	}

	@Test
	void raisesASubtopicWhoseNamePieceIsInTheTitle() {
		Evaluation evaluation = evaluate(input("정치권 국회 본회의", "전체기사"));

		assertThat(evaluation.topics()).containsEntry("politics.assembly", 0.9);
		// 그 대분류의 중분류가 일치했으므로 대분류 이름 일치로 다른 중분류를 고르지 않는다
		assertThat(evaluation.topics().get("politics.executive")).isLessThan(0.12);
		assertThat(values(evaluation.topics(), "economy.finance", "economy.realestate"))
			.allSatisfy(v -> assertThat(v).isLessThan(0.12));
	}

	@Test
	void picksOneSubtopicAndTheFirstRegionWhenNothingMatches() {
		Evaluation evaluation = evaluate(input("날씨 맑음", "전체기사"));

		assertThat(evaluation.topics().values()).filteredOn(v -> v == 0.7).hasSize(1);
		assertThat(evaluation.topics().values()).filteredOn(v -> v != 0.7)
			.hasSize(3)
			.allSatisfy(v -> assertThat(v).isBetween(0.0, 0.12).isLessThan(0.12));
		// 체계 순서의 첫 지역이다 — 고정 체계는 키 순서(domestic)와 체계 순서(world)가 다르다
		assertThat(evaluation.regions()).containsEntry("world", 0.7);
		assertThat(evaluation.regions().get("domestic")).isLessThan(0.12);
	}

	@Test
	void picksTheFirstRegionAndFormatWhateverTheTitle() {
		// 제목이 달라도 일치가 없으면 늘 체계 순서의 첫 지역 · 첫 선택지다 — 제목에 따라 고르지 않는다
		for (String title : List.of("날씨 맑음", "날씨 흐림", "주말 나들이 인파", "새 학기 시작", "단풍 절정", "연휴 귀성길")) {
			Evaluation evaluation = evaluate(input(title, "전체기사"));

			assertThat(evaluation.regions()).as(title).containsEntry("world", 0.7);
			assertThat(evaluation.formats()).as(title).containsEntry("straight", 0.8);
		}
	}

	@Test
	void doesNotMatchANamePieceShorterThanTwoCharacters() {
		TagQuestions oneCharacter = new TagQuestions(QUESTIONS.topics(),
				List.of(new AskedTag("korea", "한", "한국", "한국의 일인가"), new AskedTag("world", "국제", "국외", "국외의 일인가")),
				QUESTIONS.format(), QUESTIONS.importance());

		Evaluation oneCharacterOnly = (Evaluation) this.evaluator.evaluate(input("한파 경보", "전체기사"), oneCharacter);
		Evaluation twoCharacters = (Evaluation) this.evaluator.evaluate(input("국제 유가", "전체기사"), oneCharacter);

		assertThat(oneCharacterOnly.regions().values()).doesNotContain(0.9);
		assertThat(twoCharacters.regions()).containsEntry("world", 0.9);
	}

	@Test
	void raisesARegionWhoseNameIsInTheTitle() {
		Evaluation evaluation = evaluate(input("국내 물가 상승", "전체기사"));

		assertThat(evaluation.regions()).containsEntry("domestic", 0.9);
		assertThat(evaluation.regions().get("world")).isLessThan(0.12);
	}

	@Test
	void choosesAFormatWhoseNameIsInACategory() {
		Evaluation evaluation = evaluate(new EvaluationInput("날씨 맑음", null,
				List.of(new FeedContext("경향신문", "전체기사", List.of("오피니언")))));

		assertThat(evaluation.formats()).containsEntry("opinion", 0.8);
		assertThat(evaluation.formats().get("straight")).isCloseTo(0.1, within(1e-12));
		assertThat(evaluation.formats().get("feature")).isCloseTo(0.1, within(1e-12));
	}

	@Test
	void choosesTheFirstFormatAndOneImportanceLevelWhenNothingMatches() {
		Evaluation evaluation = evaluate(input("날씨 맑음", "전체기사"));

		// 체계 순서의 첫 선택지다 — 고정 체계는 키 순서(feature)와 체계 순서(straight)가 다르다
		assertThat(evaluation.formats()).containsEntry("straight", 0.8);
		assertThat(evaluation.formats().get("opinion")).isCloseTo(0.1, within(1e-12));
		assertThat(evaluation.formats().get("feature")).isCloseTo(0.1, within(1e-12));
		assertThat(evaluation.importance().values()).filteredOn(v -> v == 0.6).hasSize(1);
		assertThat(evaluation.importance().values()).filteredOn(v -> v != 0.6)
			.hasSize(4)
			.allSatisfy(v -> assertThat(v).isCloseTo(0.1, within(1e-12)));
	}

	@Test
	void ignoresNamesInTheSummaryOrTheSourceName() {
		Evaluation named = evaluate(new EvaluationInput("날씨 맑음", "국회 정치 국내 오피니언",
				List.of(new FeedContext("정치 국회 국내 오피니언 신문", "전체기사", List.of()))));
		Evaluation plain = evaluate(input("날씨 맑음", "전체기사"));

		// 같은 제목 · 섹션에서 일치가 없을 때와 같은 확률이다
		assertThat(named.topics()).isEqualTo(plain.topics());
		assertThat(named.regions()).isEqualTo(plain.regions());
		assertThat(named.formats()).isEqualTo(plain.formats());
		assertThat(named.topics().values()).noneMatch(v -> v == 0.9 || v == 0.8);
	}

	@Test
	void failsWithTheKindOfTheMarkerInTheTitle() {
		assertThat(this.evaluator.evaluate(input("[fake:transient] 속보", "정치"), QUESTIONS))
			.isEqualTo(new EvaluationFailure(EvaluatorFailureKind.TRANSIENT, "Fake 평가기 표지에 따른 실패"));
		assertThat(this.evaluator.evaluate(input("속보 [fake:article-rejected]", "정치"), QUESTIONS))
			.isEqualTo(new EvaluationFailure(EvaluatorFailureKind.ARTICLE_REJECTED, "Fake 평가기 표지에 따른 실패"));
		assertThat(this.evaluator.evaluate(input("[fake:evaluator-unavailable]", "정치"), QUESTIONS))
			.isEqualTo(new EvaluationFailure(EvaluatorFailureKind.EVALUATOR_UNAVAILABLE, "Fake 평가기 표지에 따른 실패"));
	}

	@Test
	void takesTheFirstKindWhenTwoMarkersArePresent() {
		assertThat(this.evaluator.evaluate(input("[fake:evaluator-unavailable] [fake:transient]", "정치"), QUESTIONS))
			.isEqualTo(new EvaluationFailure(EvaluatorFailureKind.TRANSIENT, "Fake 평가기 표지에 따른 실패"));
	}

	@Test
	void evaluatesAnUnknownMarkerOrAMarkerOutsideTheTitle() {
		assertThat(this.evaluator.evaluate(input("[fake:unknown] 속보", "정치"), QUESTIONS)).isInstanceOf(Evaluation.class);
		assertThat(this.evaluator.evaluate(input("속보", "[fake:transient]"), QUESTIONS)).isInstanceOf(Evaluation.class);
	}

	@Test
	void countsHalfTheCodePointsOfInputAndQuestionsRoundedUp() {
		TagQuestions small = new TagQuestions(
				List.of(new TopicCategory("t", "대분류 이름은 세지 않는다", "설명도 세지 않는다",
						List.of(new AskedTag("t.s", "이름", "설명", "q")))),
				List.of(new AskedTag("r", "이름", "설명", "r")),
				new FormatChoice("f", List.of(new FormatOption("a", "a", "설명", false),
						new FormatOption("b", "b", "설명", false))),
				new ImportanceScale("i", List.of(new ImportanceLevel(1, "1"), new ImportanceLevel(2, "2"),
						new ImportanceLevel(3, "3"), new ImportanceLevel(4, "4"), new ImportanceLevel(5, "5"))));
		EvaluationInput input = new EvaluationInput("가나다", null, List.of(new FeedContext("다", "라", List.of("마"))));

		// 제목 3 + 출처 · 섹션 · 카테고리 3 + 중분류 질문 1 + 지역 질문 1 + 형식 질문 1 + 선택지 이름 2 + 중요도 질문 1 + 기준 5 = 17
		Evaluation evaluation = (Evaluation) this.evaluator.evaluate(input, small);

		assertThat(evaluation.inputTokens()).isEqualTo(9);
		// 요약 2 를 더하면 19
		EvaluationInput withSummary = new EvaluationInput("가나다", "바사",
				List.of(new FeedContext("다", "라", List.of("마"))));
		assertThat(((Evaluation) this.evaluator.evaluate(withSummary, small)).inputTokens()).isEqualTo(10);
	}

	@Test
	@SuppressWarnings("unchecked")
	void writesTheRawResponseAsJsonWithSortedKeys() {
		Evaluation evaluation = evaluate(input("날씨 맑음", "전체기사"));

		Map<String, Object> raw = JsonMapper.builder().build().readValue(evaluation.rawResponse(), LinkedHashMap.class);

		assertThat(raw).containsEntry("evaluator", "fake").containsEntry("modelVersion", "fake-rules-2");
		assertThat(raw.keySet()).containsExactly("evaluator", "modelVersion", "topics", "regions", "formats",
				"importance");
		assertThat(keys(raw, "topics")).containsExactly("economy.finance", "economy.realestate", "politics.assembly",
				"politics.executive");
		assertThat(keys(raw, "regions")).containsExactly("domestic", "world");
		assertThat(keys(raw, "formats")).containsExactly("feature", "opinion", "straight");
		assertThat(keys(raw, "importance")).containsExactly("1", "2", "3", "4", "5");
	}

	@Test
	void judgesMembershipByTheSharedPiecesOfTheTitles() {
		MembershipInput input = membership("서울 시장 선거 결과 발표");
		String raw = "{\"evaluator\":\"fake\",\"modelVersion\":\"fake-rules-2\",\"events\":{\"12\":0.41,\"15\":0.05}}";

		assertThat(this.evaluator.judgeMembership(input))
			.isEqualTo(new Membership(Map.of(12L, 0.41, 15L, 0.05), "fake", "fake-rules-2", 25, raw));
		assertThat(this.evaluator.judgeMembership(input)).isEqualTo(this.evaluator.judgeMembership(input));
	}

	@Test
	void judgesMembershipAtTheFloorWithoutPiecesInTheJudgedTitle() {
		assertThat(((Membership) this.evaluator.judgeMembership(membership("가 · 나"))).probabilities())
			.isEqualTo(Map.of(12L, 0.05, 15L, 0.05));
	}

	@Test
	void failsTheMembershipWithTheKindOfTheFirstMarkerInTheJudgedTitle() {
		assertThat(this.evaluator.judgeMembership(membership("[fake:article-rejected] 서울 시장")))
			.isEqualTo(new EvaluationFailure(EvaluatorFailureKind.ARTICLE_REJECTED, "Fake 평가기 표지에 따른 실패"));
		assertThat(this.evaluator.judgeMembership(membership("[fake:evaluator-unavailable] [fake:transient]")))
			.isEqualTo(new EvaluationFailure(EvaluatorFailureKind.TRANSIENT, "Fake 평가기 표지에 따른 실패"));
		assertThat(this.evaluator.judgeMembership(membership("서울 [fake:evaluator-unavailable]")))
			.isEqualTo(new EvaluationFailure(EvaluatorFailureKind.EVALUATOR_UNAVAILABLE, "Fake 평가기 표지에 따른 실패"));
	}

	/** 후보 12(서술 "서울 시장 개표") · 15(서술 "부산 항만 파업"), 요약 없음 */
	private static MembershipInput membership(String title) {
		return new MembershipInput(title, null, List.of(new CandidateEvent(12, List.of("서울 시장 개표")),
				new CandidateEvent(15, List.of("부산 항만 파업"))), new MembershipWording("w-test", "같은 사건인가"));
	}

	private Evaluation evaluate(EvaluationInput input) {
		EvaluationResult result = this.evaluator.evaluate(input, QUESTIONS);
		assertThat(result).isInstanceOf(Evaluation.class);
		return (Evaluation) result;
	}

	private static EvaluationInput input(String title, String section) {
		return new EvaluationInput(title, null, List.of(new FeedContext("동아일보", section, List.of())));
	}

	private static List<Double> values(Map<String, Double> probabilities, String... keys) {
		return List.of(keys).stream().map(probabilities::get).toList();
	}

	@SuppressWarnings("unchecked")
	private static List<String> keys(Map<String, Object> raw, String name) {
		return List.copyOf(((Map<String, Object>) raw.get(name)).keySet());
	}

}
