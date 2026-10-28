package net.yozmnews.source.domain.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 상태 코드 규칙 — 코드마다 기대 결과를 손으로 적은 표.
 */
class StatusRuleTests {

	@Test
	void okReadsTheBody() {
		assertThat(StatusRule.classify(200, false)).isEqualTo(new StatusOutcome.Body());
	}

	@Test
	void notModifiedNeedsAConditionalRequest() {
		assertThat(StatusRule.classify(304, true)).isEqualTo(new StatusOutcome.NotModified());
		assertThat(StatusRule.classify(304, false))
			.isEqualTo(new StatusOutcome.Failure(FetchFailureReason.UNEXPECTED_STATUS));
	}

	@ParameterizedTest
	@CsvSource({ "301, true", "308, true", "302, false", "303, false", "307, false" })
	void redirects(int status, boolean permanent) {
		assertThat(StatusRule.classify(status, true)).isEqualTo(new StatusOutcome.Redirect(permanent));
	}

	@ParameterizedTest
	@CsvSource({ "300, UNEXPECTED_STATUS", "204, UNEXPECTED_STATUS", "206, UNEXPECTED_STATUS",
			"100, UNEXPECTED_STATUS", "401, ACCESS_DENIED", "403, ACCESS_DENIED", "404, NOT_FOUND", "410, NOT_FOUND",
			"408, THROTTLED", "429, THROTTLED", "418, CLIENT_ERROR", "400, CLIENT_ERROR", "499, CLIENT_ERROR",
			"500, SERVER_ERROR", "503, SERVER_ERROR", "599, SERVER_ERROR", "600, UNEXPECTED_STATUS",
			"999, UNEXPECTED_STATUS" })
	void failures(int status, FetchFailureReason reason) {
		assertThat(StatusRule.classify(status, false)).isEqualTo(new StatusOutcome.Failure(reason));
	}

}
