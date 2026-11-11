package net.yozmnews.evaluation.domain.evaluator;

import static net.yozmnews.evaluation.domain.result.ResultTagSet.ASSEMBLY;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.EXECUTIVE;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.FINANCE;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.TAG_SET;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import net.yozmnews.evaluation.adapter.out.fake.FakeArticleEvaluator;
import org.junit.jupiter.api.Test;

/**
 * 결과와 체계의 일치 — 태그 종류마다 키 집합이 정확히 같고, 원본 응답이 JSON 이고 U+0000 이 없는가.
 * <p>
 * 지키는 규칙: evaluation-R15
 */
class ResultCheckTests {

	private static final Map<String, Double> TOPICS = Map.of(EXECUTIVE, 0.5, ASSEMBLY, 0.5, FINANCE, 0.5);

	private static final Map<String, Double> REGIONS = Map.of("world", 0.5, "domestic", 0.5);

	private static final Map<String, Double> FORMATS = Map.of("straight", 0.5, "opinion", 0.25, "feature", 0.25);

	private static final Map<Integer, Double> IMPORTANCE = Map.of(1, 0.2, 2, 0.2, 3, 0.2, 4, 0.2, 5, 0.2);

	@Test
	void aResultOfTheFakeEvaluatorMatchesTheTagSet() {
		EvaluationResult result = new FakeArticleEvaluator().evaluate(new EvaluationInput("국회 본회의", null, List.of()),
				TAG_SET.questions());

		assertThat(result).isInstanceOf(Evaluation.class);
		assertThat(ResultCheck.mismatch((Evaluation) result, TAG_SET.questions())).isEmpty();
	}

	@Test
	void aMissingSubtopicIsAMismatch() {
		Evaluation evaluation = evaluation(Map.of(EXECUTIVE, 0.5, FINANCE, 0.5), REGIONS, FORMATS, IMPORTANCE, "{}");

		assertThat(ResultCheck.mismatch(evaluation, TAG_SET.questions()))
			.contains("주제 키가 체계와 다르다 — 빠진 키 [politics.assembly], 모르는 키 []");
	}

	@Test
	void anUnknownRegionIsAMismatch() {
		Evaluation evaluation = evaluation(TOPICS, Map.of("world", 0.5, "domestic", 0.5, "moon", 0.1), FORMATS,
				IMPORTANCE, "{}");

		assertThat(ResultCheck.mismatch(evaluation, TAG_SET.questions()))
			.contains("지역 키가 체계와 다르다 — 빠진 키 [], 모르는 키 [moon]");
	}

	@Test
	void aMissingFormatOptionIsAMismatch() {
		Evaluation evaluation = evaluation(TOPICS, REGIONS, Map.of("straight", 0.5, "opinion", 0.5), IMPORTANCE, "{}");

		assertThat(ResultCheck.mismatch(evaluation, TAG_SET.questions()))
			.contains("형식 키가 체계와 다르다 — 빠진 키 [feature], 모르는 키 []");
	}

	@Test
	void aSixthImportanceLevelIsAMismatch() {
		Evaluation evaluation = evaluation(TOPICS, REGIONS, FORMATS,
				Map.of(1, 0.2, 2, 0.2, 3, 0.2, 4, 0.2, 5, 0.1, 6, 0.1), "{}");

		assertThat(ResultCheck.mismatch(evaluation, TAG_SET.questions()))
			.contains("중요도 수준 키가 체계와 다르다 — 빠진 키 [], 모르는 키 [6]");
	}

	@Test
	void aRawResponseThatIsNotJsonIsAMismatch() {
		assertThat(ResultCheck.mismatch(evaluation(TOPICS, REGIONS, FORMATS, IMPORTANCE, "not json"), TAG_SET.questions()))
			.contains("원본 응답이 JSON 이 아니다");
		assertThat(ResultCheck.mismatch(evaluation(TOPICS, REGIONS, FORMATS, IMPORTANCE, ""), TAG_SET.questions()))
			.contains("원본 응답이 JSON 이 아니다");
		// JSON 값 하나 뒤에 다른 것이 붙은 원문 — PostgreSQL jsonb 가 받지 않는다
		assertThat(ResultCheck.mismatch(evaluation(TOPICS, REGIONS, FORMATS, IMPORTANCE, "{} {}"),
				TAG_SET.questions()))
			.contains("원본 응답이 JSON 이 아니다");
	}

	@Test
	void aNulCharacterAnywhereInTheRawResponseIsAMismatch() {
		// JSON 이스케이프 \u0000 — 값에서도 키에서도
		assertThat(ResultCheck.mismatch(evaluation(TOPICS, REGIONS, FORMATS, IMPORTANCE, "{\"a\":[\"x\\u0000\"]}"),
				TAG_SET.questions()))
			.contains("원본 응답에 문자 U+0000 이 있다");
		assertThat(ResultCheck.mismatch(evaluation(TOPICS, REGIONS, FORMATS, IMPORTANCE, "{\"\\u0000\":1}"),
				TAG_SET.questions()))
			.contains("원본 응답에 문자 U+0000 이 있다");
	}

	@Test
	void anUnpairedSurrogateAnywhereInTheRawResponseIsAMismatch() {
		// JSON 이스케이프 — 값의 짝 없는 상위 서로게이트, 키의 짝 없는 하위 서로게이트
		assertThat(ResultCheck.mismatch(evaluation(TOPICS, REGIONS, FORMATS, IMPORTANCE, "{\"a\":\"x\\ud800\"}"),
				TAG_SET.questions()))
			.contains("원본 응답에 짝 없는 서로게이트가 있다");
		assertThat(ResultCheck.mismatch(evaluation(TOPICS, REGIONS, FORMATS, IMPORTANCE, "{\"\\udc00\":1}"),
				TAG_SET.questions()))
			.contains("원본 응답에 짝 없는 서로게이트가 있다");
	}

	@Test
	void aPairedSurrogateIsNotAMismatch() {
		// U+1F600 — 이스케이프한 짝과 원문 그대로의 글자
		assertThat(ResultCheck.mismatch(
				evaluation(TOPICS, REGIONS, FORMATS, IMPORTANCE, "{\"a\":\"\\ud83d\\ude00 \uD83D\uDE00\"}"),
				TAG_SET.questions()))
			.isEmpty();
	}

	@Test
	void anEscapedBackslashBeforeU0000IsNotANulCharacter() {
		// 원문 {"a":"\\u0000"} — 값은 백슬래시 하나와 글자 u0000 이다
		assertThat(ResultCheck.mismatch(evaluation(TOPICS, REGIONS, FORMATS, IMPORTANCE, "{\"a\":\"\\\\u0000\"}"),
				TAG_SET.questions()))
			.isEmpty();
	}

	private static Evaluation evaluation(Map<String, Double> topics, Map<String, Double> regions,
			Map<String, Double> formats, Map<Integer, Double> importance, String rawResponse) {
		return new Evaluation(topics, regions, formats, importance, "jev", "jev-1", 1, rawResponse);
	}

}
