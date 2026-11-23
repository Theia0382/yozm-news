package net.yozmnews.user.domain.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * 시도 기록 — 호출은 하나 이상이고, 연속 실패 수와 마지막 실패의 코드 · 시각은 함께 있거나 함께 없다.
 */
class SummaryAttemptTests {

	private static final Instant AT = Instant.parse("2026-11-20T01:00:00Z");

	@Test
	void aFailureCodeOrTimeWithoutConsecutiveFailuresIsRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new SummaryAttempt(1, 1, 0, 0, 0, "OUTPUT_TRUNCATED", null));
		assertThatIllegalArgumentException().isThrownBy(() -> new SummaryAttempt(1, 1, 0, 0, 0, null, AT));
	}

	@Test
	void consecutiveFailuresWithoutTheCodeOrTheTimeAreRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new SummaryAttempt(1, 1, 0, 0, 1, null, AT));
		assertThatIllegalArgumentException().isThrownBy(() -> new SummaryAttempt(1, 1, 0, 0, 1, "OUTPUT_TRUNCATED", null));
	}

	@Test
	void aRecordWithoutACallOrWithNegativeNumbersIsRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new SummaryAttempt(1, 0, 0, 0, 0, null, null));
		assertThatIllegalArgumentException().isThrownBy(() -> new SummaryAttempt(1, 1, -1, 0, 0, null, null));
		assertThatIllegalArgumentException().isThrownBy(() -> new SummaryAttempt(1, 1, 0, -1, 0, null, null));
		assertThatIllegalArgumentException().isThrownBy(() -> new SummaryAttempt(1, 1, 0, 0, -1, null, null));
	}

	@Test
	void bothShapesOfAValidRecordAreAccepted() {
		// 대조 — 연속 실패가 없는 기록과 있는 기록
		assertThat(new SummaryAttempt(1, 1, 0, 0, 0, null, null).failures()).isZero();
		assertThat(new SummaryAttempt(1, 2, 500, 80, 2, "OUTPUT_TRUNCATED", AT).failedAt()).isEqualTo(AT);
	}

}
