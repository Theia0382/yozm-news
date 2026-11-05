package net.yozmnews.evaluation.adapter.out.jev;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static net.yozmnews.evaluation.EvaluatorFailureKind.ARTICLE_REJECTED;
import static net.yozmnews.evaluation.EvaluatorFailureKind.EVALUATOR_UNAVAILABLE;
import static net.yozmnews.evaluation.EvaluatorFailureKind.TRANSIENT;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
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
import net.yozmnews.evaluation.domain.evaluator.RereadFailure;
import net.yozmnews.evaluation.domain.jev.JevApiKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Jev 평가기 — WireMock 을 Jev 자리에 두고 요청 형태 · 응답 변환 · 오류 갈래 · 마감 · 본문 상한 · 문맥 초과 확인 요청 · 키 노출을 본다.
 * 응답 본문은 실측 응답 · 공식 문서의 모양이다. 기본 주소에 경로가 있는 경우(OpenRouter 의 호환 경로처럼)를 쓴다.
 * <p>
 * 지키는 규칙: evaluation-R14 · evaluation-R20 · ADR-0058 · docs/security.md(키가 로그 · 문구에 남지 않는다)
 */
@ExtendWith(OutputCaptureExtension.class)
class JevArticleEvaluatorTests {

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	private static final String PATH = "/api/v1/systemone";

	private static final String KEY = "tsk-wiremock-only-0123456789";

	private static final Duration SHORT = Duration.ofSeconds(1);

	private static final TagSetView TAG_SET = new TagSetView(new TagSetId(1), 1,
			List.of(new TopicCategory("economy", "경제", "경제",
					List.of(new AskedTag("economy.real-estate", "부동산", "부동산 시장", "주제가 부동산 시장인가")))),
			List.of(new AskedTag("domestic", "국내", "대한민국", "무대가 대한민국인가")),
			new FormatChoice("형식은 무엇인가", List.of(new FormatOption("straight", "스트레이트", "사실 보도", false),
					new FormatOption("opinion", "오피니언", "의견", true))),
			new ImportanceScale("얼마나 중요한가", List.of(new ImportanceLevel(1, "작다"), new ImportanceLevel(2, "크다"))));

	private static final EvaluationInput INPUT = new EvaluationInput("공동시행자 관리 강화", null,
			List.of(new FeedContext("경향신문", "경제", List.of("부동산"))));

	private static final String REQUEST = "{\"model\":\"jev-latest\","
			+ "\"state\":\"출처: 경향신문 / 피드 섹션: 경제 / 카테고리: 부동산\\n제목: 공동시행자 관리 강화\",\"questions\":{"
			+ "\"topic.economy.real-estate\":{\"type\":\"noul\",\"instructions\":\"주제가 부동산 시장인가\"},"
			+ "\"region.domestic\":{\"type\":\"noul\",\"instructions\":\"무대가 대한민국인가\"},"
			+ "\"format\":{\"type\":\"choice\",\"instructions\":\"형식은 무엇인가\",\"criteria\":{\"straight\":\"사실 보도\",\"opinion\":\"의견\"}},"
			+ "\"importance\":{\"type\":\"score\",\"instructions\":\"얼마나 중요한가\",\"criteria\":[\"작다\",\"크다\"]}}}";

	private static final String ANSWER = "{\"model\":\"jev-1.14.0\",\"answers\":{"
			+ "\"topic.economy.real-estate\":{\"type\":\"noul\",\"noul\":0.93},"
			+ "\"region.domestic\":{\"type\":\"noul\",\"noul\":0.99},"
			+ "\"format\":{\"type\":\"choice\",\"choice\":\"straight\",\"confidence\":0.98,\"probabilities\":{\"straight\":0.98,\"opinion\":0.02}},"
			+ "\"importance\":{\"type\":\"score\",\"score\":0.21,\"confidence\":0.6,\"legend\":{\"0\":\"작다\",\"1\":\"크다\"},"
			+ "\"probabilities\":{\"0\":0.79,\"1\":0.21}}},\"usage\":{\"input_tokens\":3208,\"output_tokens\":1077}}";

	private static final String BAD_KEY = "{\"detail\":{\"error_type\":\"authentication_error\","
			+ "\"message\":\"Cannot authenticate with the server. Please check your API key and try again.\"}}";

	private static final String CONTEXT_EXCEEDED = "{\"detail\":{\"error_type\":\"max_tokens_exceeded\"}}";

	private final List<JevArticleEvaluator> evaluators = new ArrayList<>();

	@AfterEach
	void close() {
		this.evaluators.forEach(JevArticleEvaluator::close);
	}

	@Test
	void sendsOneRequestWithTheKeyAndReadsTheAnswer() {
		server.stubFor(post(PATH).willReturn(json(200, ANSWER).withHeader("x-typesafe-request-id", "req_ok")));

		EvaluationResult result = evaluator().evaluate(INPUT, TAG_SET);

		assertThat(result).isEqualTo(new Evaluation(Map.of("economy.real-estate", 0.93), Map.of("domestic", 0.99),
				Map.of("straight", 0.98, "opinion", 0.02), Map.of(1, 0.79, 2, 0.21), "jev", "jev-1.14.0", 3208, ANSWER));
		server.verify(1, postRequestedFor(urlEqualTo(PATH)).withHeader("Authorization", equalTo("Bearer " + KEY))
			.withHeader("Content-Type", containing("application/json"))
			.withHeader("Accept", equalTo("application/json"))
			.withRequestBody(equalToJson(REQUEST)));
	}

	@Test
	void reportsABadKeyAsUnavailable() {
		server.stubFor(post(PATH).willReturn(json(401, BAD_KEY).withHeader("x-typesafe-request-id", "req_401")));

		assertThat(evaluator().evaluate(INPUT, TAG_SET)).isEqualTo(new EvaluationFailure(EVALUATOR_UNAVAILABLE,
				"Jev 가 평가 요청을 받지 않았다 — Jev 응답 401 authentication_error: Cannot authenticate with the server. "
						+ "Please check your API key and try again. (요청 req_401)"));
	}

	@Test
	void reportsAMissingKeyAndAValidationErrorAsUnavailable() {
		server.stubFor(post(PATH).willReturn(json(403,
				"{\"detail\":{\"error_type\":\"authentication_error\",\"message\":\"Must supply an API key! Check your request and try again.\"}}")));
		assertThat(evaluator().evaluate(INPUT, TAG_SET)).isEqualTo(new EvaluationFailure(EVALUATOR_UNAVAILABLE,
				"Jev 가 평가 요청을 받지 않았다 — Jev 응답 403 authentication_error: Must supply an API key! Check your request and try again."));

		server.stubFor(post(PATH).willReturn(json(422,
				"{\"detail\":[{\"type\":\"missing\",\"loc\":[\"body\",\"state\"],\"msg\":\"Field required\",\"input\":\"공동시행자 관리 강화\"}]}")));
		assertThat(evaluator().evaluate(INPUT, TAG_SET)).isEqualTo(new EvaluationFailure(EVALUATOR_UNAVAILABLE,
				"Jev 가 평가 요청을 받지 않았다 — Jev 응답 422 missing: Field required"));
	}

	@Test
	void reportsALimitWithRetryAfterAsTransient() {
		server.stubFor(post(PATH).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "7")
			.withHeader("x-typesafe-request-id", "req_429")));

		assertThat(evaluator().evaluate(INPUT, TAG_SET)).isEqualTo(new EvaluationFailure(TRANSIENT,
				"Jev 가 평가 요청을 받지 않았다 — Jev 응답 429 · Retry-After 7 (요청 req_429)"));
	}

	@Test
	void reportsServerErrorsAsTransient() {
		server.stubFor(post(PATH).willReturn(aResponse().withStatus(503).withBody("<html>unavailable</html>")));
		assertThat(evaluator().evaluate(INPUT, TAG_SET))
			.isEqualTo(new EvaluationFailure(TRANSIENT, "Jev 가 평가 요청을 받지 않았다 — Jev 응답 503"));

		server.stubFor(post(PATH).willReturn(aResponse().withStatus(529)));
		assertThat(evaluator().evaluate(INPUT, TAG_SET))
			.isEqualTo(new EvaluationFailure(TRANSIENT, "Jev 가 평가 요청을 받지 않았다 — Jev 응답 529"));
	}

	@Test
	void reportsARefusedConnectionAsTransient() throws IOException {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}

		assertThat(evaluator(URI.create("http://127.0.0.1:" + closedPort), SHORT).evaluate(INPUT, TAG_SET))
			.isEqualTo(new EvaluationFailure(TRANSIENT, "Jev 와 주고받지 못했다 — ConnectException"));
	}

	@Test
	void reportsLateHeadersAsTransient() {
		server.stubFor(post(PATH).willReturn(json(200, ANSWER).withFixedDelay(2_000)));

		assertThat(evaluator(SHORT).evaluate(INPUT, TAG_SET))
			.isEqualTo(new EvaluationFailure(TRANSIENT, "Jev 응답 마감(1초)을 넘었다"));
	}

	@Test
	void appliesTheDeadlineToABodyThatDrips() {
		server.stubFor(post(PATH).willReturn(json(200, ANSWER).withChunkedDribbleDelay(10, 3_000)));

		long start = System.nanoTime();
		EvaluationResult result = evaluator(SHORT).evaluate(INPUT, TAG_SET);

		assertThat(result).isEqualTo(new EvaluationFailure(TRANSIENT, "Jev 응답 마감(1초)을 넘었다"));
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
	}

	@Test
	void readsTheSameAnswerWithoutDelayUnderTheShortDeadline() {
		server.stubFor(post(PATH).willReturn(json(200, ANSWER)));

		assertThat(evaluator(SHORT).evaluate(INPUT, TAG_SET)).isInstanceOf(Evaluation.class);
	}

	@Test
	void reportsABodyOverTheLimitWithItsLengthAsUnavailable() {
		server.stubFor(post(PATH).willReturn(
				aResponse().withStatus(200).withHeader("Content-Length", "1048577").withBody("a".repeat(1_048_577))));

		assertThat(evaluator().evaluate(INPUT, TAG_SET)).isEqualTo(new EvaluationFailure(EVALUATOR_UNAVAILABLE,
				"Jev 응답 본문이 상한(1048576 바이트)을 넘는다"));
	}

	@Test
	void reportsAChunkedBodyOverTheLimitAsUnavailable() {
		server.stubFor(post(PATH)
			.willReturn(aResponse().withStatus(200).withBody("a".repeat(1_048_577)).withChunkedDribbleDelay(4, 200)));

		assertThat(evaluator().evaluate(INPUT, TAG_SET)).isEqualTo(new EvaluationFailure(EVALUATOR_UNAVAILABLE,
				"Jev 응답 본문이 상한(1048576 바이트)을 넘는다"));
	}

	@Test
	void readsABodyOfExactlyTheLimit() {
		server.stubFor(post(PATH).willReturn(aResponse().withStatus(200).withBody("a".repeat(1_048_576))));

		assertThat(evaluator().evaluate(INPUT, TAG_SET)).isEqualTo(
				new EvaluationFailure(EVALUATOR_UNAVAILABLE, "Jev 응답을 읽을 수 없다 — JSON 이 아니다"));
	}

	@Test
	void blamesTheArticleWhenTheEmptyStateFits() {
		contextExceededThenProbe(json(200, ANSWER).withHeader("x-typesafe-request-id", "req_probe"));

		assertThat(evaluator().evaluate(INPUT, TAG_SET)).isEqualTo(new EvaluationFailure(ARTICLE_REJECTED,
				"기사가 Jev 문맥 한도를 넘는다 — 빈 state 확인 요청은 받았다 — Jev 응답 400 max_tokens_exceeded (요청 req_first)"));
		assertProbed();
	}

	@Test
	void blamesTheTagSetWhenTheEmptyStateAlsoExceeds() {
		contextExceededThenProbe(json(400, CONTEXT_EXCEEDED).withHeader("x-typesafe-request-id", "req_probe"));

		assertThat(evaluator().evaluate(INPUT, TAG_SET)).isEqualTo(new EvaluationFailure(EVALUATOR_UNAVAILABLE,
				"Jev 문맥 초과 — 빈 state 확인 요청도 받지 않았다 — Jev 응답 400 max_tokens_exceeded (요청 req_probe)"));
		assertProbed();
	}

	@Test
	void retriesLaterWhenTheProbeFailsForAWhile() {
		contextExceededThenProbe(aResponse().withStatus(503));

		assertThat(evaluator().evaluate(INPUT, TAG_SET)).isEqualTo(new EvaluationFailure(TRANSIENT,
				"Jev 문맥 초과 — 빈 state 확인 요청도 받지 않았다 — Jev 응답 503"));
		assertProbed();
	}

	@Test
	void retriesLaterWhenTheProbeMissesTheDeadline() {
		contextExceededThenProbe(json(200, ANSWER).withFixedDelay(2_000));

		assertThat(evaluator(SHORT).evaluate(INPUT, TAG_SET)).isEqualTo(new EvaluationFailure(TRANSIENT,
				"Jev 문맥 초과 — 빈 state 확인 요청이 실패했다: Jev 응답 마감(1초)을 넘었다 — Jev 응답 400 max_tokens_exceeded (요청 req_first)"));
		assertProbed();
	}

	@Test
	void reportsAProbeBodyOverTheLimitAsUnavailable() {
		contextExceededThenProbe(aResponse().withStatus(200).withBody("a".repeat(1_048_577)));

		assertThat(evaluator().evaluate(INPUT, TAG_SET)).isEqualTo(new EvaluationFailure(EVALUATOR_UNAVAILABLE,
				"Jev 문맥 초과 — 빈 state 확인 요청: Jev 응답 본문이 상한(1048576 바이트)을 넘는다"));
		assertProbed();
	}

	@Test
	void keepsTheKeyOutOfMessagesAndOutput(CapturedOutput output) {
		List<String> messages = new ArrayList<>();
		for (ResponseDefinitionBuilder response : List.of(json(401, BAD_KEY), json(422, "{\"detail\":[]}"),
				aResponse().withStatus(429).withHeader("Retry-After", "1"), aResponse().withStatus(500),
				json(200, "{\"model\":\"jev-1.14.0\"}"), json(400, CONTEXT_EXCEEDED))) {
			server.stubFor(post(PATH).willReturn(response));
			messages.add(((EvaluationFailure) evaluator().evaluate(INPUT, TAG_SET)).message());
		}
		server.stubFor(post(PATH).willReturn(json(200, ANSWER).withFixedDelay(2_000)));
		messages.add(((EvaluationFailure) evaluator(SHORT).evaluate(INPUT, TAG_SET)).message());

		server.verify(postRequestedFor(urlEqualTo(PATH)).withHeader("Authorization", equalTo("Bearer " + KEY)));
		assertThat(messages).hasSize(7).allSatisfy(message -> assertThat(message).isNotBlank().doesNotContain(KEY));
		assertThat(output.getAll()).doesNotContain(KEY);
	}

	@Test
	void rereadsAStoredAnswerWithoutSendingARequest() {
		assertThat(evaluator().reread("jev", ANSWER, TAG_SET)).isEqualTo(new Evaluation(
				Map.of("economy.real-estate", 0.93), Map.of("domestic", 0.99), Map.of("straight", 0.98, "opinion", 0.02),
				Map.of(1, 0.79, 2, 0.21), "jev", "jev-1.14.0", 3208, ANSWER));
		server.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void doesNotRereadTheResponseOfAnotherEvaluator() {
		assertThat(evaluator().reread("fake", ANSWER, TAG_SET)).isEqualTo(
				new RereadFailure(RereadFailure.Kind.OTHER_EVALUATOR, "Jev 가 남긴 원본 응답이 아니다 — 평가기 fake"));
	}

	@Test
	void aStoredAnswerWithoutAnAnswerOfTheTagSetIsUnreadable() {
		String answer = ANSWER.replace("\"region.domestic\":{\"type\":\"noul\",\"noul\":0.99},", "");

		assertThat(evaluator().reread("jev", answer, TAG_SET)).isEqualTo(new RereadFailure(RereadFailure.Kind.UNREADABLE,
				"Jev 응답을 읽을 수 없다 — 답이 없다 — region.domestic"));
		server.verify(0, anyRequestedFor(anyUrl()));
	}

	private void contextExceededThenProbe(ResponseDefinitionBuilder probe) {
		server.stubFor(post(PATH).atPriority(1)
			.withRequestBody(matchingJsonPath("$.state", equalTo("")))
			.willReturn(probe));
		server.stubFor(post(PATH).atPriority(2)
			.willReturn(json(400, CONTEXT_EXCEEDED).withHeader("x-typesafe-request-id", "req_first")));
	}

	private static void assertProbed() {
		server.verify(2, anyRequestedFor(anyUrl()));
		server.verify(1, postRequestedFor(urlEqualTo(PATH)).withRequestBody(matchingJsonPath("$.state", equalTo(""))));
	}

	private static ResponseDefinitionBuilder json(int status, String body) {
		return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
	}

	private JevArticleEvaluator evaluator() {
		return evaluator(JevArticleEvaluator.RESPONSE_DEADLINE);
	}

	private JevArticleEvaluator evaluator(Duration deadline) {
		return evaluator(URI.create(server.baseUrl() + "/api/"), deadline);
	}

	private JevArticleEvaluator evaluator(URI baseUrl, Duration deadline) {
		JevArticleEvaluator evaluator = new JevArticleEvaluator(baseUrl, "jev-latest", JevApiKey.of(KEY), deadline);
		this.evaluators.add(evaluator);
		return evaluator;
	}

}
