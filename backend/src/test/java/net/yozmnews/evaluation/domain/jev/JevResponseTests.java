package net.yozmnews.evaluation.domain.jev;

import static net.yozmnews.evaluation.domain.jev.JevFixtures.TAG_SET;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import net.yozmnews.evaluation.domain.evaluator.Evaluation;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;
import org.junit.jupiter.api.Test;

/**
 * Jev 의 200 응답을 포트 결과로 — 답의 이름 · 종류 · 수준 위치, 읽을 수 없는 응답은 평가기 사용 불가.
 * <p>
 * 지키는 규칙: evaluation-R14 · evaluation-R15(포트는 분포만)
 */
class JevResponseTests {

	/** 실측 응답의 모양 — 여분의 답 하나(체계에 없음)를 함께 둔다 */
	private static final String BODY = """
			{"model":"jev-1.14.0","answers":{\
			"topic.politics.executive":{"type":"noul","noul":0.19},\
			"topic.politics.legislature":{"type":"noul","noul":0.92},\
			"region.us":{"type":"noul","noul":0.02},\
			"region.domestic":{"type":"noul","noul":0.97},\
			"format":{"type":"choice","choice":"straight","confidence":0.96,"probabilities":{"straight":0.97,"opinion":0.03}},\
			"importance":{"type":"score","score":1.55,"confidence":0.62,"legend":{"0":"가벼운 화제","1":"업계 소식","2":"사회 전반"},\
			"probabilities":{"0":0.04,"1":0.37,"2":0.59}},\
			"topic.removed":{"type":"noul","noul":0.5}},\
			"usage":{"input_tokens":3245,"output_tokens":1077}}""";

	@Test
	void readsEveryAnswerIntoTheEvaluation() {
		assertThat(JevResponse.read(BODY, "req_1", TAG_SET)).isEqualTo(new Evaluation(
				Map.of("politics.executive", 0.19, "politics.legislature", 0.92), Map.of("us", 0.02, "domestic", 0.97),
				Map.of("straight", 0.97, "opinion", 0.03), Map.of(1, 0.04, 2, 0.37, 3, 0.59), "jev", "jev-1.14.0",
				3245, BODY));
	}

	@Test
	void isUnavailableWithoutAnAnswer() {
		String body = BODY.replace("\"topic.politics.legislature\":{\"type\":\"noul\",\"noul\":0.92},", "");

		assertThat(JevResponse.read(body, "req_1", TAG_SET)).isEqualTo(unavailable("답이 없다 — topic.politics.legislature (요청 req_1)"));
	}

	@Test
	void isUnavailableWithAnAnswerOfAnotherType() {
		String body = BODY.replace("\"region.us\":{\"type\":\"noul\"", "\"region.us\":{\"type\":\"score\"");

		assertThat(JevResponse.read(body, null, TAG_SET)).isEqualTo(unavailable("답의 종류가 다르다 (기대 noul) — region.us"));
	}

	@Test
	void isUnavailableWithAProbabilityAboveOne() {
		String body = BODY.replace("\"1\":0.37", "\"1\":1.2");

		assertThat(JevResponse.read(body, null, TAG_SET)).isEqualTo(unavailable("확률이 0 ~ 1 이 아니다 — importance.1"));
	}

	@Test
	void isUnavailableWithAProbabilityThatIsNotANumber() {
		String body = BODY.replace("\"opinion\":0.03", "\"opinion\":\"0.03\"");

		assertThat(JevResponse.read(body, null, TAG_SET)).isEqualTo(unavailable("확률이 없거나 숫자가 아니다 — format.opinion"));
	}

	@Test
	void isUnavailableWithAMissingLevel() {
		String body = BODY.replace(",\"2\":0.59", "");

		assertThat(JevResponse.read(body, null, TAG_SET)).isEqualTo(unavailable("확률이 없거나 숫자가 아니다 — importance.2"));
	}

	@Test
	void isUnavailableWithoutInputTokens() {
		String body = BODY.replace("\"input_tokens\":3245,", "");

		assertThat(JevResponse.read(body, null, TAG_SET))
			.isEqualTo(unavailable("usage.input_tokens 가 0 이상의 정수가 아니다"));
	}

	@Test
	void isUnavailableWithoutAModel() {
		String body = BODY.replace("\"model\":\"jev-1.14.0\",", "");

		assertThat(JevResponse.read(body, null, TAG_SET)).isEqualTo(unavailable("model 이 없거나 비었다"));
	}

	@Test
	void isUnavailableWithABlankModel() {
		String body = BODY.replace("\"model\":\"jev-1.14.0\"", "\"model\":\" \"");

		assertThat(JevResponse.read(body, null, TAG_SET)).isEqualTo(unavailable("model 이 없거나 비었다"));
	}

	@Test
	void isUnavailableWithNegativeInputTokens() {
		String body = BODY.replace("\"input_tokens\":3245", "\"input_tokens\":-1");

		assertThat(JevResponse.read(body, null, TAG_SET))
			.isEqualTo(unavailable("usage.input_tokens 가 0 이상의 정수가 아니다"));
	}

	@Test
	void isUnavailableWithANegativeProbability() {
		String body = BODY.replace("\"noul\":0.02", "\"noul\":-0.02");

		assertThat(JevResponse.read(body, null, TAG_SET)).isEqualTo(unavailable("확률이 0 ~ 1 이 아니다 — region.us"));
	}

	@Test
	void isUnavailableForJsonThatIsNotAnObject() {
		assertThat(JevResponse.read("[]", null, TAG_SET)).isEqualTo(unavailable("JSON 객체가 아니다"));
	}

	@Test
	void isUnavailableForATextThatIsNotJson() {
		assertThat(JevResponse.read("<html>bad gateway</html>", "req_2", TAG_SET))
			.isEqualTo(unavailable("JSON 이 아니다 (요청 req_2)"));
	}

	private static EvaluationFailure unavailable(String problem) {
		return new EvaluationFailure(EvaluationFailure.Kind.EVALUATOR_UNAVAILABLE, "Jev 응답을 읽을 수 없다 — " + problem);
	}

}
