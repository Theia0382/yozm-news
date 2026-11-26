package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * 뭉치 결과 · 회차 기록 요약 · 대기 현황의 값 검사.
 * <p>
 * 지키는 규칙: platform-R130
 */
class RoundBatchTests {

	@Test
	void aBatchThatTookItsLimitIsFull() {
		assertThat(RoundBatch.finished(32, 32)).isEqualTo(new RoundBatch(32, BatchEnd.FULL));
		assertThat(RoundBatch.finished(33, 32)).isEqualTo(new RoundBatch(33, BatchEnd.FULL));
	}

	@Test
	void aBatchThatTookLessThanItsLimitDrainedTheQueue() {
		assertThat(RoundBatch.finished(31, 32)).isEqualTo(new RoundBatch(31, BatchEnd.DRAINED));
		assertThat(RoundBatch.finished(0, 32)).isEqualTo(new RoundBatch(0, BatchEnd.DRAINED));
	}

	@Test
	void backingOffAndStoppingKeepTheirCount() {
		assertThat(RoundBatch.backedOff(5)).isEqualTo(new RoundBatch(5, BatchEnd.BACKED_OFF));
		assertThat(RoundBatch.stopped(0)).isEqualTo(new RoundBatch(0, BatchEnd.STOPPED));
	}

	@Test
	void countsAndLimitsAreChecked() {
		assertThatIllegalArgumentException().isThrownBy(() -> new RoundBatch(-1, BatchEnd.FULL));
		assertThatIllegalArgumentException().isThrownBy(() -> RoundBatch.finished(0, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new RoundSummary(-1, 0, null));
		assertThatIllegalArgumentException().isThrownBy(() -> new RoundSummary(0, -1, null));
	}

	@Test
	void deferredItemsAreAmongTheWaitingOnes() {
		assertThat(new RoundQueue("event-assignment", 3, 3, RoundSummary.NONE).deferred()).isEqualTo(3);
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new RoundQueue("event-assignment", 3, 4, RoundSummary.NONE));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new RoundQueue("event-assignment", -1, 0, RoundSummary.NONE));
	}

	@Test
	void noRecordIsZeroWithoutALastRound() {
		assertThat(RoundSummary.NONE).isEqualTo(new RoundSummary(0, 0, null));
		Instant at = Instant.parse("2026-11-26T12:00:00Z");
		assertThat(new RoundSummary.LastRound(at, at, 0, 0, RoundEnd.FAILED).batches()).isZero();
	}

}
