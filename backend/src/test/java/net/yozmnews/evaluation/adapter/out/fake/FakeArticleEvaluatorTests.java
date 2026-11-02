package net.yozmnews.evaluation.adapter.out.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.FormatChoice;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.ImportanceScale;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.evaluation.domain.evaluator.Evaluation;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;
import net.yozmnews.evaluation.domain.evaluator.EvaluationInput;
import net.yozmnews.evaluation.domain.evaluator.EvaluationResult;
import net.yozmnews.evaluation.domain.evaluator.FeedContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 고정 규칙 평가기 — 결정성 · 채움 · 이름 일치 · 실패 표지 · 입력 토큰 · 원본 응답. 해시로 고른 위치는 단언하지 않고 개수와 규칙이 정한
 * 값을 본다.
 */
class FakeArticleEvaluatorTests {

	/** 고정 체계 — 대분류 2 × 중분류 2, 지역 2, 형식 3, 중요도 1 ~ 5. 지역 · 형식은 체계 순서와 키 순서가 다르다. */
	private static final TagSetView TAG_SET = new TagSetView(new TagSetId(1), 1,
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

	@Test
	void givesTheSameResultForTheSameInput() {
		EvaluationInput input = input("날씨 맑음", "전체기사");

		assertThat(this.evaluator.evaluate(input, TAG_SET)).isEqualTo(this.evaluator.evaluate(input, TAG_SET))
			.isEqualTo(new FakeArticleEvaluator().evaluate(input, TAG_SET));
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
		assertThat(evaluation.modelVersion()).isEqualTo("fake-rules-1");
	}

	@Test
	void picksOneSubtopicOfACategoryNamedInTheSection() {
		Evaluation evaluation = evaluate(input("대통령 시정연설", "정치"));

		assertThat(values(evaluation.topics(), "politics.executive", "politics.assembly")).filteredOn(v -> v == 0.8)
			.hasSize(1);
		assertThat(values(evaluation.topics(), "politics.executive", "politics.assembly")).filteredOn(v -> v != 0.8)
			.allSatisfy(v -> assertThat(v).isLessThan(0.3));
		assertThat(values(evaluation.topics(), "economy.finance", "economy.realestate"))
			.allSatisfy(v -> assertThat(v).isLessThan(0.3));
	}

	@Test
	void raisesASubtopicWhoseNamePieceIsInTheTitle() {
		Evaluation evaluation = evaluate(input("정치권 국회 본회의", "전체기사"));

		assertThat(evaluation.topics()).containsEntry("politics.assembly", 0.9);
		// 그 대분류의 중분류가 일치했으므로 대분류 이름 일치로 다른 중분류를 고르지 않는다
		assertThat(evaluation.topics().get("politics.executive")).isLessThan(0.3);
		assertThat(values(evaluation.topics(), "economy.finance", "economy.realestate"))
			.allSatisfy(v -> assertThat(v).isLessThan(0.3));
	}

	@Test
	void picksOneSubtopicAndOneRegionWhenNothingMatches() {
		Evaluation evaluation = evaluate(input("날씨 맑음", "전체기사"));

		assertThat(evaluation.topics().values()).filteredOn(v -> v == 0.7).hasSize(1);
		assertThat(evaluation.topics().values()).filteredOn(v -> v != 0.7)
			.hasSize(3)
			.allSatisfy(v -> assertThat(v).isBetween(0.0, 0.3).isLessThan(0.3));
		assertThat(evaluation.regions().values()).filteredOn(v -> v == 0.7).hasSize(1);
		assertThat(evaluation.regions().values()).filteredOn(v -> v != 0.7)
			.singleElement()
			.satisfies(v -> assertThat(v).isLessThan(0.3));
	}

	@Test
	void doesNotMatchANamePieceShorterThanTwoCharacters() {
		TagSetView oneCharacter = new TagSetView(new TagSetId(1), 1, TAG_SET.topics(),
				List.of(new AskedTag("korea", "한", "한국", "한국의 일인가"), new AskedTag("world", "국제", "국외", "국외의 일인가")),
				TAG_SET.format(), TAG_SET.importance());

		Evaluation oneCharacterOnly = (Evaluation) this.evaluator.evaluate(input("한파 경보", "전체기사"), oneCharacter);
		Evaluation twoCharacters = (Evaluation) this.evaluator.evaluate(input("국제 유가", "전체기사"), oneCharacter);

		assertThat(oneCharacterOnly.regions().values()).doesNotContain(0.9);
		assertThat(twoCharacters.regions()).containsEntry("world", 0.9);
	}

	@Test
	void raisesARegionWhoseNameIsInTheTitle() {
		Evaluation evaluation = evaluate(input("국내 물가 상승", "전체기사"));

		assertThat(evaluation.regions()).containsEntry("domestic", 0.9);
		assertThat(evaluation.regions().get("world")).isLessThan(0.3);
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
	void choosesOneFormatAndOneImportanceLevelWhenNothingMatches() {
		Evaluation evaluation = evaluate(input("날씨 맑음", "전체기사"));

		assertThat(evaluation.formats().values()).filteredOn(v -> v == 0.8).hasSize(1);
		assertThat(evaluation.formats().values()).filteredOn(v -> v != 0.8)
			.hasSize(2)
			.allSatisfy(v -> assertThat(v).isCloseTo(0.1, within(1e-12)));
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
		assertThat(this.evaluator.evaluate(input("[fake:transient] 속보", "정치"), TAG_SET))
			.isEqualTo(new EvaluationFailure(EvaluationFailure.Kind.TRANSIENT, "Fake 평가기 표지에 따른 실패"));
		assertThat(this.evaluator.evaluate(input("속보 [fake:article-rejected]", "정치"), TAG_SET))
			.isEqualTo(new EvaluationFailure(EvaluationFailure.Kind.ARTICLE_REJECTED, "Fake 평가기 표지에 따른 실패"));
		assertThat(this.evaluator.evaluate(input("[fake:evaluator-unavailable]", "정치"), TAG_SET))
			.isEqualTo(new EvaluationFailure(EvaluationFailure.Kind.EVALUATOR_UNAVAILABLE, "Fake 평가기 표지에 따른 실패"));
	}

	@Test
	void takesTheFirstKindWhenTwoMarkersArePresent() {
		assertThat(this.evaluator.evaluate(input("[fake:evaluator-unavailable] [fake:transient]", "정치"), TAG_SET))
			.isEqualTo(new EvaluationFailure(EvaluationFailure.Kind.TRANSIENT, "Fake 평가기 표지에 따른 실패"));
	}

	@Test
	void evaluatesAnUnknownMarkerOrAMarkerOutsideTheTitle() {
		assertThat(this.evaluator.evaluate(input("[fake:unknown] 속보", "정치"), TAG_SET)).isInstanceOf(Evaluation.class);
		assertThat(this.evaluator.evaluate(input("속보", "[fake:transient]"), TAG_SET)).isInstanceOf(Evaluation.class);
	}

	@Test
	void countsHalfTheCodePointsOfInputAndQuestionsRoundedUp() {
		TagSetView small = new TagSetView(new TagSetId(1), 1,
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

		assertThat(raw).containsEntry("evaluator", "fake").containsEntry("modelVersion", "fake-rules-1");
		assertThat(raw.keySet()).containsExactly("evaluator", "modelVersion", "topics", "regions", "formats",
				"importance");
		assertThat(keys(raw, "topics")).containsExactly("economy.finance", "economy.realestate", "politics.assembly",
				"politics.executive");
		assertThat(keys(raw, "regions")).containsExactly("domestic", "world");
		assertThat(keys(raw, "formats")).containsExactly("feature", "opinion", "straight");
		assertThat(keys(raw, "importance")).containsExactly("1", "2", "3", "4", "5");
	}

	private Evaluation evaluate(EvaluationInput input) {
		EvaluationResult result = this.evaluator.evaluate(input, TAG_SET);
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
