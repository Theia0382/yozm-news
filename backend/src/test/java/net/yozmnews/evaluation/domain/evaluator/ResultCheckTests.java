package net.yozmnews.evaluation.domain.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.FormatChoice;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.ImportanceScale;
import net.yozmnews.evaluation.TagQuestions;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.evaluation.adapter.out.fake.FakeArticleEvaluator;
import org.junit.jupiter.api.Test;

/**
 * 결과와 체계의 일치 — 태그 종류마다 키 집합이 정확히 같고, 원본 응답이 JSON 이고 U+0000 이 없는가.
 * <p>
 * 지키는 규칙: evaluation-R41
 */
class ResultCheckTests {

	private static final String EXECUTIVE = "politics.executive";

	private static final String ASSEMBLY = "politics.assembly";

	private static final String FINANCE = "economy.finance";

	/** 질문 묶음 — 대분류 정치(행정부 · 국회) · 경제(금융), 지역 국외 · 국내, 형식 셋, 중요도 1 ~ 5 */
	private static final TagQuestions QUESTIONS = new TagQuestions(
			List.of(new TopicCategory("politics", "정치", "정치", List.of(
					new AskedTag(EXECUTIVE, "대통령실·행정부", "대통령실과 행정부", "대통령실과 행정부를 다루는가"),
					new AskedTag(ASSEMBLY, "국회·정당·선거", "국회와 정당", "국회와 정당을 다루는가"))),
					new TopicCategory("economy", "경제", "경제",
							List.of(new AskedTag(FINANCE, "금융", "금융", "금융을 다루는가")))),
			List.of(new AskedTag("world", "국제", "국외", "국외의 일인가"),
					new AskedTag("domestic", "국내", "국내", "국내의 일인가")),
			new FormatChoice("이 기사의 형식은", List.of(new FormatOption("straight", "스트레이트", "사실 보도", false),
					new FormatOption("opinion", "오피니언", "의견", true),
					new FormatOption("feature", "기획", "기획 기사", false))),
			new ImportanceScale("얼마나 중요한가", List.of(new ImportanceLevel(1, "사소"), new ImportanceLevel(2, "작음"),
					new ImportanceLevel(3, "보통"), new ImportanceLevel(4, "큼"), new ImportanceLevel(5, "매우 큼"))));

	private static final Map<String, Double> TOPICS = Map.of(EXECUTIVE, 0.5, ASSEMBLY, 0.5, FINANCE, 0.5);

	private static final Map<String, Double> REGIONS = Map.of("world", 0.5, "domestic", 0.5);

	private static final Map<String, Double> FORMATS = Map.of("straight", 0.5, "opinion", 0.25, "feature", 0.25);

	private static final Map<Integer, Double> IMPORTANCE = Map.of(1, 0.2, 2, 0.2, 3, 0.2, 4, 0.2, 5, 0.2);

	@Test
	void aResultOfTheFakeEvaluatorMatchesTheTagSet() {
		EvaluationResult result = new FakeArticleEvaluator().evaluate(new EvaluationInput("국회 본회의", null, List.of()),
				QUESTIONS);

		assertThat(result).isInstanceOf(Evaluation.class);
		assertThat(ResultCheck.mismatch((Evaluation) result, QUESTIONS)).isEmpty();
	}

	@Test
	void aMissingSubtopicIsAMismatch() {
		Evaluation evaluation = evaluation(Map.of(EXECUTIVE, 0.5, FINANCE, 0.5), REGIONS, FORMATS, IMPORTANCE, "{}");

		assertThat(ResultCheck.mismatch(evaluation, QUESTIONS))
			.contains("주제 키가 체계와 다르다 — 빠진 키 [politics.assembly], 모르는 키 []");
	}

	@Test
	void anUnknownRegionIsAMismatch() {
		Evaluation evaluation = evaluation(TOPICS, Map.of("world", 0.5, "domestic", 0.5, "moon", 0.1), FORMATS,
				IMPORTANCE, "{}");

		assertThat(ResultCheck.mismatch(evaluation, QUESTIONS))
			.contains("지역 키가 체계와 다르다 — 빠진 키 [], 모르는 키 [moon]");
	}

	@Test
	void aMissingFormatOptionIsAMismatch() {
		Evaluation evaluation = evaluation(TOPICS, REGIONS, Map.of("straight", 0.5, "opinion", 0.5), IMPORTANCE, "{}");

		assertThat(ResultCheck.mismatch(evaluation, QUESTIONS))
			.contains("형식 키가 체계와 다르다 — 빠진 키 [feature], 모르는 키 []");
	}

	@Test
	void aSixthImportanceLevelIsAMismatch() {
		Evaluation evaluation = evaluation(TOPICS, REGIONS, FORMATS,
				Map.of(1, 0.2, 2, 0.2, 3, 0.2, 4, 0.2, 5, 0.1, 6, 0.1), "{}");

		assertThat(ResultCheck.mismatch(evaluation, QUESTIONS))
			.contains("중요도 수준 키가 체계와 다르다 — 빠진 키 [], 모르는 키 [6]");
	}

	@Test
	void aRawResponseThatIsNotJsonIsAMismatch() {
		assertThat(ResultCheck.mismatch(evaluation(TOPICS, REGIONS, FORMATS, IMPORTANCE, "not json"), QUESTIONS))
			.contains("원본 응답이 JSON 이 아니다");
		assertThat(ResultCheck.mismatch(evaluation(TOPICS, REGIONS, FORMATS, IMPORTANCE, ""), QUESTIONS))
			.contains("원본 응답이 JSON 이 아니다");
		// JSON 값 하나 뒤에 다른 것이 붙은 원문 — PostgreSQL jsonb 가 받지 않는다
		assertThat(ResultCheck.mismatch(evaluation(TOPICS, REGIONS, FORMATS, IMPORTANCE, "{} {}"),
				QUESTIONS))
			.contains("원본 응답이 JSON 이 아니다");
	}

	@Test
	void aNulCharacterAnywhereInTheRawResponseIsAMismatch() {
		// JSON 이스케이프 \u0000 — 값에서도 키에서도
		assertThat(ResultCheck.mismatch(evaluation(TOPICS, REGIONS, FORMATS, IMPORTANCE, "{\"a\":[\"x\\u0000\"]}"),
				QUESTIONS))
			.contains("원본 응답에 문자 U+0000 이 있다");
		assertThat(ResultCheck.mismatch(evaluation(TOPICS, REGIONS, FORMATS, IMPORTANCE, "{\"\\u0000\":1}"),
				QUESTIONS))
			.contains("원본 응답에 문자 U+0000 이 있다");
	}

	@Test
	void anUnpairedSurrogateAnywhereInTheRawResponseIsAMismatch() {
		// JSON 이스케이프 — 값의 짝 없는 상위 서로게이트, 키의 짝 없는 하위 서로게이트
		assertThat(ResultCheck.mismatch(evaluation(TOPICS, REGIONS, FORMATS, IMPORTANCE, "{\"a\":\"x\\ud800\"}"),
				QUESTIONS))
			.contains("원본 응답에 짝 없는 서로게이트가 있다");
		assertThat(ResultCheck.mismatch(evaluation(TOPICS, REGIONS, FORMATS, IMPORTANCE, "{\"\\udc00\":1}"),
				QUESTIONS))
			.contains("원본 응답에 짝 없는 서로게이트가 있다");
	}

	@Test
	void aPairedSurrogateIsNotAMismatch() {
		// U+1F600 — 이스케이프한 짝과 원문 그대로의 글자
		assertThat(ResultCheck.mismatch(
				evaluation(TOPICS, REGIONS, FORMATS, IMPORTANCE, "{\"a\":\"\\ud83d\\ude00 \uD83D\uDE00\"}"),
				QUESTIONS))
			.isEmpty();
	}

	@Test
	void anEscapedBackslashBeforeU0000IsNotANulCharacter() {
		// 원문 {"a":"\\u0000"} — 값은 백슬래시 하나와 글자 u0000 이다
		assertThat(ResultCheck.mismatch(evaluation(TOPICS, REGIONS, FORMATS, IMPORTANCE, "{\"a\":\"\\\\u0000\"}"),
				QUESTIONS))
			.isEmpty();
	}

	private static Evaluation evaluation(Map<String, Double> topics, Map<String, Double> regions,
			Map<String, Double> formats, Map<Integer, Double> importance, String rawResponse) {
		return new Evaluation(topics, regions, formats, importance, "jev", "jev-1", 1, rawResponse);
	}

}
