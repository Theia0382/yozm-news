package net.yozmnews.evaluation.domain.jev;

import static net.yozmnews.evaluation.EvaluatorFailureKind.ARTICLE_REJECTED;
import static net.yozmnews.evaluation.EvaluatorFailureKind.EVALUATOR_UNAVAILABLE;
import static net.yozmnews.evaluation.EvaluatorFailureKind.TRANSIENT;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Jev 오류 응답의 갈래와 운영자용 문구 — 본문은 실측 응답의 모양이다.
 * <p>
 * 지키는 규칙: evaluation-R20(실패 갈래) · ADR-0058
 */
class JevErrorsTests {

	private static final String CONTEXT_EXCEEDED = "{\"detail\":{\"error_type\":\"max_tokens_exceeded\"}}";

	private static final String UNKNOWN_MODEL = "{\"detail\":{\"error_type\":\"api_usage_error\",\"message\":\"Unknown model: jev-9.9.9\"}}";

	private static final String BAD_KEY = "{\"detail\":{\"error_type\":\"authentication_error\","
			+ "\"message\":\"Cannot authenticate with the server. Please check your API key and try again.\"}}";

	/** 검증 실패 — 원소의 input 이 요청 본문을 되돌려준다 */
	private static final String VALIDATION = "{\"detail\":[{\"type\":\"missing\",\"loc\":[\"body\",\"state\"],"
			+ "\"msg\":\"Field required\",\"input\":\"{\\\"state\\\":\\\"기사 원문 문장\\\"}\"}]}";

	@Test
	void sendsAContextExceededErrorToTheProbe() {
		assertThat(JevErrors.classify(400, CONTEXT_EXCEEDED)).isEqualTo(new JevOutcome.ContextExceeded());
	}

	@Test
	void treatsAnotherBadRequestAsUnavailable() {
		assertThat(JevErrors.classify(400, UNKNOWN_MODEL)).isEqualTo(new JevOutcome.Failed(EVALUATOR_UNAVAILABLE));
	}

	@ParameterizedTest
	@ValueSource(ints = { 401, 402, 403, 404, 413, 415, 422, 302, 204 })
	void treatsRequestAndSettingErrorsAsUnavailable(int status) {
		assertThat(JevErrors.classify(status, BAD_KEY)).isEqualTo(new JevOutcome.Failed(EVALUATOR_UNAVAILABLE));
	}

	@ParameterizedTest
	@ValueSource(ints = { 408, 429, 500, 502, 503, 529, 599 })
	void treatsTimeoutsLimitsAndServerErrorsAsTransient(int status) {
		assertThat(JevErrors.classify(status, "")).isEqualTo(new JevOutcome.Failed(TRANSIENT));
	}

	@Test
	void doesNotProbeAContextExceededMarkerOnAnotherStatus() {
		assertThat(JevErrors.classify(422, CONTEXT_EXCEEDED)).isEqualTo(new JevOutcome.Failed(EVALUATOR_UNAVAILABLE));
	}

	@Test
	void blamesTheArticleWhenTheProbeIsAccepted() {
		assertThat(JevErrors.afterProbe(200, "{}")).isEqualTo(ARTICLE_REJECTED);
	}

	@Test
	void blamesTheTagSetWhenTheProbeAlsoExceedsTheContext() {
		assertThat(JevErrors.afterProbe(400, CONTEXT_EXCEEDED)).isEqualTo(EVALUATOR_UNAVAILABLE);
	}

	@Test
	void keepsTheProbeFailureKindOtherwise() {
		assertThat(JevErrors.afterProbe(503, "")).isEqualTo(TRANSIENT);
		assertThat(JevErrors.afterProbe(401, BAD_KEY)).isEqualTo(EVALUATOR_UNAVAILABLE);
	}

	@Test
	void writesTheStatusTypeMessageAndRequestId() {
		assertThat(JevErrors.message("평가 요청 실패", 401, BAD_KEY, null, "req_01a1"))
			.isEqualTo("평가 요청 실패 — Jev 응답 401 authentication_error: Cannot authenticate with the server. "
					+ "Please check your API key and try again. (요청 req_01a1)");
	}

	@Test
	void writesRetryAfter() {
		assertThat(JevErrors.message("평가 요청 실패", 429, "", "7", null))
			.isEqualTo("평가 요청 실패 — Jev 응답 429 · Retry-After 7");
	}

	@Test
	void cleansAndCutsRetryAfterAndTheRequestId() {
		assertThat(JevErrors.message("평가 요청 실패", 429, "", "7\r\nX-Injected: 1", "r".repeat(100)))
			.isEqualTo("평가 요청 실패 — Jev 응답 429 · Retry-After 7  X-Injected: 1 (요청 " + "r".repeat(80) + ")");
	}

	@Test
	void writesAStringDetail() {
		assertThat(JevErrors.message("평가 요청 실패", 400, "{\"detail\":\"Question key cannot be empty.\"}", null, null))
			.isEqualTo("평가 요청 실패 — Jev 응답 400: Question key cannot be empty.");
	}

	@Test
	void leavesTheEchoedRequestOutOfAValidationMessage() {
		assertThat(VALIDATION).contains("기사 원문 문장");

		assertThat(JevErrors.message("평가 요청 실패", 422, VALIDATION, null, null))
			.isEqualTo("평가 요청 실패 — Jev 응답 422 missing: Field required");
	}

	@Test
	void writesOnlyTheStatusForABodyThatIsNotJson() {
		assertThat(JevErrors.message("평가 요청 실패", 502, "<html>Bad gateway</html>", null, null))
			.isEqualTo("평가 요청 실패 — Jev 응답 502");
	}

	@Test
	void replacesControlCharactersAndLoneSurrogates() {
		String body = "{\"detail\":{\"error_type\":\"api_usage_error\",\"message\":\"a\\u0000b\\nc\"}}";

		assertThat(JevErrors.message("평가 요청 실패", 400, body, null, "req\uD800"))
			.isEqualTo("평가 요청 실패 — Jev 응답 400 api_usage_error: a b c (요청 req\uFFFD)");
	}

	@Test
	void cutsTheJevMessageAt200CodePoints() {
		String body = "{\"detail\":{\"error_type\":\"api_usage_error\",\"message\":\"" + "😀".repeat(300) + "\"}}";

		assertThat(JevErrors.message("실패", 400, body, null, null))
			.isEqualTo("실패 — Jev 응답 400 api_usage_error: " + "😀".repeat(200));
	}

	@Test
	void cutsTheWholeMessageAt500CodePoints() {
		String message = JevErrors.message("😀".repeat(600), 503, "", null, null);

		assertThat(message).isEqualTo("😀".repeat(500));
	}

}
