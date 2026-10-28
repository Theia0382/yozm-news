package net.yozmnews.source.domain.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import net.yozmnews.source.domain.feed.DocumentProblem;
import net.yozmnews.source.domain.feed.DocumentProblemKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 실패 이유의 일시 · 영구 분류(확인 요청 C-1 포함)와 실패 값의 불변식.
 */
class FetchFailureReasonTests {

	@Test
	void hasSixteenReasons() {
		assertThat(FetchFailureReason.values()).hasSize(16);
	}

	@ParameterizedTest
	@CsvSource({ "TIMEOUT, TEMPORARY", "CONNECTION_FAILED, TEMPORARY", "NAME_NOT_RESOLVED, TEMPORARY",
			"SERVER_ERROR, TEMPORARY", "THROTTLED, TEMPORARY", "ACCESS_DENIED, PERMANENT", "NOT_FOUND, PERMANENT",
			"CLIENT_ERROR, PERMANENT", "UNEXPECTED_STATUS, PERMANENT", "TOO_MANY_REDIRECTS, PERMANENT",
			"INVALID_REDIRECT, PERMANENT", "UNSUPPORTED_URL, PERMANENT", "BLOCKED_ADDRESS, PERMANENT",
			"TLS_FAILURE, PERMANENT", "TOO_LARGE, PERMANENT", "UNREADABLE, PERMANENT" })
	void classifiesEachReason(FetchFailureReason reason, FailurePermanence expected) {
		assertThat(reason.permanence()).isEqualTo(expected);
	}

	@Test
	void statusFailureQuotesTheCode() {
		FetchFailure failure = FetchFailure.status(FetchFailureReason.NOT_FOUND, 404);

		assertThat(failure.message()).isEqualTo("피드가 없다는 응답 (HTTP 404)");
		assertThat(failure.httpStatus()).isEqualTo(404);
		assertThat(failure.problem()).isNull();
		assertThat(failure.permanence()).isEqualTo(FailurePermanence.PERMANENT);
	}

	@Test
	void failureWithoutResponseAppendsTheDetail() {
		assertThat(FetchFailure.of(FetchFailureReason.BLOCKED_ADDRESS, "10.0.0.1").message())
			.isEqualTo("내부 주소로 가는 요청을 막았다: 10.0.0.1");
		assertThat(FetchFailure.of(FetchFailureReason.TIMEOUT, "").message()).isEqualTo("수집 시간 상한을 넘었다");
		assertThat(FetchFailure.of(FetchFailureReason.TIMEOUT, "").httpStatus()).isNull();
	}

	@Test
	void unreadableCarriesTheDocumentProblem() {
		DocumentProblem problem = new DocumentProblem(DocumentProblemKind.NOT_A_FEED, "RSS · Atom 피드가 아니다", null,
				null);

		FetchFailure failure = FetchFailure.unreadable(problem);

		assertThat(failure.reason()).isEqualTo(FetchFailureReason.UNREADABLE);
		assertThat(failure.message()).isEqualTo("RSS · Atom 피드가 아니다");
		assertThat(failure.problem()).isEqualTo(problem);
	}

	@Test
	void problemBelongsOnlyToUnreadable() {
		DocumentProblem problem = new DocumentProblem(DocumentProblemKind.MALFORMED_XML, "형식", 1, 1);

		assertThatIllegalArgumentException()
			.isThrownBy(() -> new FetchFailure(FetchFailureReason.TIMEOUT, "m", null, problem));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new FetchFailure(FetchFailureReason.UNREADABLE, "m", null, null));
	}

	@ParameterizedTest
	@CsvSource({ "99", "1000", "-1" })
	void rejectsAStatusOutsideThreeDigits(int status) {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new FetchFailure(FetchFailureReason.UNEXPECTED_STATUS, "m", status, null));
	}

	@Test
	void acceptsThreeDigitBoundaries() {
		assertThat(new FetchFailure(FetchFailureReason.UNEXPECTED_STATUS, "m", 100, null).httpStatus()).isEqualTo(100);
		assertThat(new FetchFailure(FetchFailureReason.UNEXPECTED_STATUS, "m", 999, null).httpStatus()).isEqualTo(999);
	}

}
