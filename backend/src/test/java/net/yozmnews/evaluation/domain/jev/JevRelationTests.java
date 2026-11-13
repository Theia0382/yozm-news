package net.yozmnews.evaluation.domain.jev;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.evaluation.EvaluatorFailureKind;
import net.yozmnews.evaluation.RelationAnswer;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;
import net.yozmnews.evaluation.domain.evaluator.FlowText;
import net.yozmnews.evaluation.domain.evaluator.HappeningText;
import net.yozmnews.evaluation.domain.evaluator.Relation;
import net.yozmnews.evaluation.domain.evaluator.RelationInput;
import net.yozmnews.evaluation.domain.evaluator.RelationWording;
import org.junit.jupiter.api.Test;

/**
 * 관계 판정의 Jev 요청 본문과 응답 읽기 — 질문 이름 · 선택지 키 · 순서는 기준을 고를 때 잰 요청의 것이다.
 * <p>
 * 지키는 규칙: evaluation-R48 · evaluation-R49
 */
class JevRelationTests {

	private static final Instant DAY = Instant.parse("2026-11-01T03:00:00Z");

	private static final RelationInput INPUT = new RelationInput(
			new FlowText(4, DAY, DAY, List.of(new HappeningText(DAY, "특검법 통과", "재석 과반"))),
			new FlowText(3, DAY, DAY, List.of(new HappeningText(DAY, "특검 후보 추천", null))), RelationWording.CURRENT);

	private static final String QUESTIONS = "\"questions\":{\"relation\":{\"type\":\"choice\","
			+ "\"instructions\":\"What is the relation between flow A and flow B?\",\"criteria\":{"
			+ "\"b_part_of_a\":\"B is narrower than A and lies inside it: B covers one reaction to A, one stage of A, "
			+ "or one strand of A, and is still about the matter of A.\","
			+ "\"a_part_of_b\":\"A is narrower than B and lies inside it: A covers one reaction to B, one stage of B, "
			+ "or one strand of B, and is still about the matter of B.\","
			+ "\"a_led_to_b\":\"B is about a different matter from A. B has its own actors and its own course and would "
			+ "go on even if A ended, but it arose because of what happened in A.\","
			+ "\"b_led_to_a\":\"A is about a different matter from B. A has its own actors and its own course and would "
			+ "go on even if B ended, but it arose because of what happened in B.\","
			+ "\"none\":\"None of the above. A and B are separate matters, or they cover the same matter as a whole, or "
			+ "one of them is a collection of separate happenings rather than one matter. They may be of a similar "
			+ "kind, involve the same people, or share a cause, but neither is inside the other and neither arose from "
			+ "the other.\"}},"
			+ "\"a_one_matter\":{\"type\":\"noul\",\"instructions\":\"Is flow A the unfolding of one matter, rather than "
			+ "a collection of separate happenings that are only of a similar kind?\"},"
			+ "\"b_one_matter\":{\"type\":\"noul\",\"instructions\":\"Is flow B the unfolding of one matter, rather than "
			+ "a collection of separate happenings that are only of a similar kind?\"}}}";

	private static final String BODY = "{\"model\":\"jev-1.14.0\",\"answers\":{\"relation\":{\"type\":\"choice\","
			+ "\"choice\":\"none\",\"confidence\":0.47,\"probabilities\":{\"b_part_of_a\":0.07,\"a_part_of_b\":0.03,"
			+ "\"a_led_to_b\":0.3,\"b_led_to_a\":0.02,\"none\":0.58}},\"a_one_matter\":{\"type\":\"noul\",\"noul\":0.47},"
			+ "\"b_one_matter\":{\"type\":\"noul\",\"noul\":0.44}},\"usage\":{\"input_tokens\":1924,\"output_tokens\":103}}";

	@Test
	void asksTheRelationAndWhetherEachFlowIsOneMatter() {
		assertThat(JevRequest.relationBody("jev-latest", INPUT)).isEqualTo("{\"model\":\"jev-latest\","
				+ "\"state\":\"Flow A (4 happenings, 11-01 to 11-01):\\n- [11-01] 특검법 통과\\n  summary: 재석 과반\\n\\n"
				+ "Flow B (3 happenings, 11-01 to 11-01):\\n- [11-01] 특검 후보 추천\"," + QUESTIONS);
	}

	@Test
	void probesWithTheSameQuestionsAndAnEmptyState() {
		assertThat(JevRequest.relationProbeBody("jev-latest", INPUT))
			.isEqualTo("{\"model\":\"jev-latest\",\"state\":\"\"," + QUESTIONS);
	}

	@Test
	void readsTheFiveOptionsIntoTheAnswersAndBothOneMatterAnswers() {
		assertThat(JevResponse.readRelation(BODY, "req_1")).isEqualTo(new Relation(
				Map.of(RelationAnswer.SECOND_PART_OF_FIRST, 0.07, RelationAnswer.FIRST_PART_OF_SECOND, 0.03,
						RelationAnswer.FIRST_LED_TO_SECOND, 0.3, RelationAnswer.SECOND_LED_TO_FIRST, 0.02,
						RelationAnswer.NONE, 0.58),
				0.47, 0.44, "jev", "jev-1.14.0", 1924, BODY));
	}

	@Test
	void anAnswerThatIsMissingOrMalformedIsUnavailable() {
		assertThat(JevResponse.readRelation(BODY.replace("\"b_led_to_a\":0.02,", ""), "req_2"))
			.isEqualTo(unavailable("Jev 응답을 읽을 수 없다 — 확률이 없거나 숫자가 아니다 — relation.b_led_to_a (요청 req_2)"));
		assertThat(JevResponse.readRelation(BODY.replace(",\"b_one_matter\":{\"type\":\"noul\",\"noul\":0.44}", ""), null))
			.isEqualTo(unavailable("Jev 응답을 읽을 수 없다 — 답이 없다 — b_one_matter"));
		assertThat(JevResponse.readRelation(BODY.replace("\"relation\":{\"type\":\"choice\"", "\"relation\":{\"type\":\"noul\""),
				null))
			.isEqualTo(unavailable("Jev 응답을 읽을 수 없다 — 답의 종류가 다르다 (기대 choice) — relation"));
		assertThat(JevResponse.readRelation(BODY.replace("0.58", "1.2"), null))
			.isEqualTo(unavailable("Jev 응답을 읽을 수 없다 — 확률이 0 ~ 1 이 아니다 — relation.none"));
	}

	private static EvaluationFailure unavailable(String message) {
		return new EvaluationFailure(EvaluatorFailureKind.EVALUATOR_UNAVAILABLE, message);
	}

}
