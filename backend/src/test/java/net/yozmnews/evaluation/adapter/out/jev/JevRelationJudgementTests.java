package net.yozmnews.evaluation.adapter.out.jev;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static net.yozmnews.evaluation.EvaluatorFailureKind.ARTICLE_REJECTED;
import static net.yozmnews.evaluation.EvaluatorFailureKind.EVALUATOR_UNAVAILABLE;
import static net.yozmnews.evaluation.EvaluatorFailureKind.TRANSIENT;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import net.yozmnews.evaluation.RelationAnswer;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;
import net.yozmnews.evaluation.domain.evaluator.FlowText;
import net.yozmnews.evaluation.domain.evaluator.HappeningText;
import net.yozmnews.evaluation.domain.evaluator.Relation;
import net.yozmnews.evaluation.domain.evaluator.RelationInput;
import net.yozmnews.evaluation.domain.evaluator.RelationWording;
import net.yozmnews.evaluation.domain.evaluator.RereadFailure;
import net.yozmnews.evaluation.domain.jev.JevApiKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Jev 평가기의 관계 판정 — 요청 하나에 질문 셋을 보내고 답을 읽는다. 실패 갈래와 문맥 초과 확인은 다른 판정과 같은 길이다. WireMock 이 Jev
 * 를 대신한다.
 * <p>
 * 지키는 규칙: evaluation-R48 · evaluation-R50(저장된 응답 본문의 다시 읽기)
 */
class JevRelationJudgementTests {

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	private static final String PATH = "/api/v1/systemone";

	private static final String KEY = "tsk-wiremock-only-0123456789";

	private static final Instant DAY = Instant.parse("2026-11-01T03:00:00Z");

	private static final RelationInput INPUT = new RelationInput(
			new FlowText(4, DAY, DAY, List.of(new HappeningText(DAY, "특검법 통과", "재석 과반"))),
			new FlowText(3, DAY, DAY, List.of(new HappeningText(DAY, "특검 후보 추천", null))), RelationWording.CURRENT);

	private static final String STATE = "Flow A (4 happenings, 11-01 to 11-01):\n- [11-01] 특검법 통과\n  summary: 재석 과반\n\n"
			+ "Flow B (3 happenings, 11-01 to 11-01):\n- [11-01] 특검 후보 추천";

	private static final String ANSWER = "{\"model\":\"jev-1.14.0\",\"answers\":{\"relation\":{\"type\":\"choice\","
			+ "\"choice\":\"b_part_of_a\",\"confidence\":0.7,\"probabilities\":{\"b_part_of_a\":0.7,\"a_part_of_b\":0.03,"
			+ "\"a_led_to_b\":0.15,\"b_led_to_a\":0.02,\"none\":0.1}},\"a_one_matter\":{\"type\":\"noul\",\"noul\":0.91},"
			+ "\"b_one_matter\":{\"type\":\"noul\",\"noul\":0.84}},\"usage\":{\"input_tokens\":1924,\"output_tokens\":103}}";

	private static final String CONTEXT_EXCEEDED = "{\"detail\":{\"error_type\":\"max_tokens_exceeded\"}}";

	private final List<JevArticleEvaluator> evaluators = new ArrayList<>();

	@AfterEach
	void close() {
		this.evaluators.forEach(JevArticleEvaluator::close);
	}

	@Test
	void asksThreeQuestionsInOneRequestAndReadsTheAnswer() {
		server.stubFor(post(PATH).willReturn(json(200, ANSWER).withHeader("x-typesafe-request-id", "req_ok")));

		assertThat(evaluator().judgeRelation(INPUT)).isEqualTo(new Relation(
				Map.of(RelationAnswer.SECOND_PART_OF_FIRST, 0.7, RelationAnswer.FIRST_PART_OF_SECOND, 0.03,
						RelationAnswer.FIRST_LED_TO_SECOND, 0.15, RelationAnswer.SECOND_LED_TO_FIRST, 0.02,
						RelationAnswer.NONE, 0.1),
				0.91, 0.84, "jev", "jev-1.14.0", 1924, ANSWER));
		server.verify(1, postRequestedFor(urlEqualTo(PATH)).withHeader("Authorization", equalTo("Bearer " + KEY))
			.withRequestBody(matchingJsonPath("$.model", equalTo("jev-latest")))
			.withRequestBody(matchingJsonPath("$.state", equalTo(STATE)))
			.withRequestBody(matchingJsonPath("$.questions.relation.type", equalTo("choice")))
			.withRequestBody(matchingJsonPath("$.questions.relation.criteria.b_led_to_a"))
			.withRequestBody(matchingJsonPath("$.questions.a_one_matter.type", equalTo("noul")))
			.withRequestBody(matchingJsonPath("$.questions.b_one_matter.type", equalTo("noul"))));
	}

	@Test
	void rereadsAStoredBodyWithoutSendingARequest() {
		// 판정 때 받은 본문 그대로 — 답 다섯과 두 한 사안 확률, 모델 · 토큰도 본문의 값이다
		assertThat(evaluator().rereadRelation("jev", ANSWER)).isEqualTo(new Relation(
				Map.of(RelationAnswer.SECOND_PART_OF_FIRST, 0.7, RelationAnswer.FIRST_PART_OF_SECOND, 0.03,
						RelationAnswer.FIRST_LED_TO_SECOND, 0.15, RelationAnswer.SECOND_LED_TO_FIRST, 0.02,
						RelationAnswer.NONE, 0.1),
				0.91, 0.84, "jev", "jev-1.14.0", 1924, ANSWER));
		server.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void doesNotReadABodyLeftByAnotherEvaluator() {
		// 같은 본문이라도 평가기 이름이 다르면 형식을 모른다고 본다
		assertThat(evaluator().rereadRelation("fake", ANSWER)).isEqualTo(new RereadFailure(
				RereadFailure.Kind.OTHER_EVALUATOR, "Jev 가 남긴 원본 응답이 아니다 — 평가기 fake"));
		server.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void cannotReadABodyWithoutAllFiveAnswers() {
		String withoutAnAnswer = ANSWER.replace("\"b_led_to_a\":0.02,", "");

		RereadFailure failure = (RereadFailure) evaluator().rereadRelation("jev", withoutAnAnswer);

		// 문구는 빠진 답의 자리만 말하고 본문의 값은 싣지 않는다
		assertThat(failure).isEqualTo(new RereadFailure(RereadFailure.Kind.UNREADABLE,
				"Jev 응답을 읽을 수 없다 — 확률이 없거나 숫자가 아니다 — relation.b_led_to_a"));
		assertThat(failure.message()).doesNotContain("0.15").doesNotContain("b_part_of_a\":0.7");
		server.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void sortsARefusedRequestIntoTheSameKinds() {
		server.stubFor(post(PATH).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "7")
			.withHeader("x-typesafe-request-id", "req_429")));

		assertThat(evaluator().judgeRelation(INPUT)).isEqualTo(new EvaluationFailure(TRANSIENT,
				"Jev 가 평가 요청을 받지 않았다 — Jev 응답 429 · Retry-After 7 (요청 req_429)"));
	}

	@Test
	void blamesTheDescriptionsWhenTheEmptyStateFits() {
		contextExceededThenProbe(json(200, ANSWER));

		assertThat(evaluator().judgeRelation(INPUT)).isEqualTo(new EvaluationFailure(ARTICLE_REJECTED,
				"기사가 Jev 문맥 한도를 넘는다 — 빈 state 확인 요청은 받았다 — Jev 응답 400 max_tokens_exceeded (요청 req_first)"));
		server.verify(2, anyRequestedFor(anyUrl()));
		server.verify(1, postRequestedFor(urlEqualTo(PATH)).withRequestBody(matchingJsonPath("$.state", equalTo("")))
			.withRequestBody(matchingJsonPath("$.questions.relation.criteria.none")));
	}

	@Test
	void isUnavailableWhenTheEmptyStateAlsoExceeds() {
		contextExceededThenProbe(json(400, CONTEXT_EXCEEDED).withHeader("x-typesafe-request-id", "req_probe"));

		assertThat(evaluator().judgeRelation(INPUT)).isEqualTo(new EvaluationFailure(EVALUATOR_UNAVAILABLE,
				"Jev 문맥 초과 — 빈 state 확인 요청도 받지 않았다 — Jev 응답 400 max_tokens_exceeded (요청 req_probe)"));
	}

	private void contextExceededThenProbe(ResponseDefinitionBuilder probe) {
		server.stubFor(post(PATH).atPriority(1)
			.withRequestBody(matchingJsonPath("$.state", equalTo("")))
			.willReturn(probe));
		server.stubFor(post(PATH).atPriority(2)
			.willReturn(json(400, CONTEXT_EXCEEDED).withHeader("x-typesafe-request-id", "req_first")));
	}

	private static ResponseDefinitionBuilder json(int status, String body) {
		return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
	}

	private JevArticleEvaluator evaluator() {
		JevArticleEvaluator evaluator = new JevArticleEvaluator(URI.create(server.baseUrl() + "/api/"), "jev-latest",
				JevApiKey.of(KEY), JevArticleEvaluator.RESPONSE_DEADLINE);
		this.evaluators.add(evaluator);
		return evaluator;
	}

}
