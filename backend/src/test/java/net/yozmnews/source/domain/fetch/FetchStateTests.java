package net.yozmnews.source.domain.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * 조건부 요청 값 — 다시 보낼 수 있는 값만 남긴다.
 */
class FetchStateTests {

	private static final String ETAG = "W/\"abc\"";

	private static final String LAST_MODIFIED = "Wed, 21 Oct 2026 07:28:00 GMT";

	@Test
	void keepsResponseValuesVerbatim() {
		FetchState state = FetchState.fromResponse(ETAG, LAST_MODIFIED);

		assertThat(state).isEqualTo(new FetchState(ETAG, LAST_MODIFIED));
		assertThat(state.isConditional()).isTrue();
	}

	@Test
	void keepsAValueOfMaximumLength() {
		String value = "a".repeat(1_024);

		assertThat(FetchState.fromResponse(value, null).etag()).isEqualTo(value);
	}

	@Test
	void dropsValuesThatCannotBeSentBack() {
		assertThat(FetchState.fromResponse("a".repeat(1_025), "x\ny")).isEqualTo(FetchState.NONE);
		assertThat(FetchState.fromResponse("\"한글\"", "\t")).isEqualTo(FetchState.NONE);
		assertThat(FetchState.fromResponse("", "   ")).isEqualTo(FetchState.NONE);
		assertThat(FetchState.fromResponse("a\u007Fb", "x\u001Fy")).isEqualTo(FetchState.NONE);
	}

	@Test
	void noneIsNotConditional() {
		assertThat(FetchState.NONE.isConditional()).isFalse();
		assertThat(new FetchState(null, LAST_MODIFIED).isConditional()).isTrue();
	}

	@Test
	void updateReplacesOnlyGivenValues() {
		FetchState previous = new FetchState(ETAG, LAST_MODIFIED);

		assertThat(previous.updatedBy("\"new\"", null)).isEqualTo(new FetchState("\"new\"", LAST_MODIFIED));
		assertThat(previous.updatedBy(null, null)).isEqualTo(previous);
		assertThat(previous.updatedBy("x\ny", "Thu, 22 Oct 2026 07:28:00 GMT"))
			.isEqualTo(new FetchState(ETAG, "Thu, 22 Oct 2026 07:28:00 GMT"));
	}

	@Test
	void rejectsAnUnstorableValueInTheConstructor() {
		assertThatIllegalArgumentException().isThrownBy(() -> new FetchState("x\ny", null));
		assertThatIllegalArgumentException().isThrownBy(() -> new FetchState(null, ""));
	}

}
