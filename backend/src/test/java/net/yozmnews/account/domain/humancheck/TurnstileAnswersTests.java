package net.yozmnews.account.domain.humancheck;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.platform.GuardedAction;
import org.junit.jupiter.api.Test;

/**
 * Turnstile 응답의 판정 — 5xx · 읽지 못하는 본문은 닿지 못함, 그 밖은 본문의 success 로 가른다. success 가 거짓이면 서비스 내부 오류도
 * 거부다. 동작 이름이 다르면 거부하되, 시험 비밀 키의 답(동작 이름이 없다)은 통과다. 응답 본문은 Turnstile 서버 검증 문서의 모양이다.
 * <p>
 * 지키는 규칙: user-R41
 */
class TurnstileAnswersTests {

	@Test
	void successWithTheSameActionPasses() {
		assertThat(TurnstileAnswers.classify(200, """
				{"success":true,"challenge_ts":"2026-11-14T10:00:00.000Z","hostname":"news.example.com",
				"error-codes":[],"action":"sign-in","cdata":""}""", GuardedAction.SIGN_IN))
			.isEqualTo(new HumanCheckResult.Passed());
	}

	@Test
	void successFromAnotherWidgetIsRejected() {
		assertThat(TurnstileAnswers.classify(200, """
				{"success":true,"hostname":"news.example.com","error-codes":[],"action":"sign-up"}""",
				GuardedAction.SIGN_IN))
			.isEqualTo(new HumanCheckResult.Rejected("action-mismatch"));
	}

	@Test
	void aTestingKeyAnswerPassesWithoutAnAction() {
		// Cloudflare 의 늘 통과 시험 비밀 키로 실제 siteverify 에 물은 답 원문 — action 이 없다
		assertThat(TurnstileAnswers.classify(200, """
				{"challenge_ts":"2026-11-14T16:58:51.131Z","error-codes":[],"hostname":"example.com",
				"metadata":{"result_with_testing_key":true},"success":true}""", GuardedAction.SIGN_UP))
			.isEqualTo(new HumanCheckResult.Passed());
	}

	@Test
	void anAnswerWithoutAnActionAndWithoutTheTestingMarkIsRejected() {
		assertThat(TurnstileAnswers.classify(200, """
				{"success":true,"hostname":"news.example.com","error-codes":[]}""", GuardedAction.SIGN_IN))
			.isEqualTo(new HumanCheckResult.Rejected("action-mismatch"));
		assertThat(TurnstileAnswers.classify(200, """
				{"success":true,"hostname":"news.example.com","error-codes":[],
				"metadata":{"result_with_testing_key":false}}""", GuardedAction.SIGN_IN))
			.isEqualTo(new HumanCheckResult.Rejected("action-mismatch"));
		assertThat(TurnstileAnswers.classify(200, """
				{"success":true,"hostname":"news.example.com","error-codes":[],"action":"sign-up",
				"metadata":{"result_with_testing_key":"true"}}""", GuardedAction.SIGN_IN))
			.isEqualTo(new HumanCheckResult.Rejected("action-mismatch"));
	}

	@Test
	void failureIsRejectedWithItsCodesEvenForAnInternalError() {
		assertThat(TurnstileAnswers.classify(200, """
				{"success":false,"error-codes":["timeout-or-duplicate"]}""", GuardedAction.SIGN_IN))
			.isEqualTo(new HumanCheckResult.Rejected("timeout-or-duplicate"));
		assertThat(TurnstileAnswers.classify(200, """
				{"success":false,"error-codes":["internal-error"]}""", GuardedAction.SIGN_IN))
			.isEqualTo(new HumanCheckResult.Rejected("internal-error"));
		assertThat(TurnstileAnswers.classify(200, """
				{"success":false,"error-codes":[]}""", GuardedAction.SIGN_IN))
			.isEqualTo(new HumanCheckResult.Rejected("no-error-code"));
	}

	@Test
	void aClientErrorStatusIsStillJudgedByItsBody() {
		assertThat(TurnstileAnswers.classify(400, """
				{"success":false,"error-codes":["bad-request"]}""", GuardedAction.SIGN_UP))
			.isEqualTo(new HumanCheckResult.Rejected("bad-request"));
	}

	@Test
	void secretErrorsAreRejectedAndMarked() {
		HumanCheckResult result = TurnstileAnswers.classify(200, """
				{"success":false,"error-codes":["invalid-input-secret"]}""", GuardedAction.SIGN_IN);

		assertThat(result).isEqualTo(new HumanCheckResult.Rejected("invalid-input-secret"));
		assertThat(TurnstileAnswers.secretRejected(result)).isTrue();
		// 대조 — 토큰 쪽 거부는 운영 설정 오류가 아니다
		assertThat(TurnstileAnswers.secretRejected(new HumanCheckResult.Rejected("timeout-or-duplicate"))).isFalse();
	}

	@Test
	void serverErrorsAndUnreadableBodiesAreUnavailable() {
		assertThat(TurnstileAnswers.classify(503, """
				{"success":false,"error-codes":["internal-error"]}""", GuardedAction.SIGN_IN))
			.isInstanceOf(HumanCheckResult.Unavailable.class);
		assertThat(TurnstileAnswers.classify(200, "<html>bad gateway</html>", GuardedAction.SIGN_IN))
			.isInstanceOf(HumanCheckResult.Unavailable.class);
		assertThat(TurnstileAnswers.classify(200, """
				{"success":"true"}""", GuardedAction.SIGN_IN)).isInstanceOf(HumanCheckResult.Unavailable.class);
		assertThat(TurnstileAnswers.classify(302, "", GuardedAction.SIGN_IN))
			.isInstanceOf(HumanCheckResult.Unavailable.class);
	}

}
