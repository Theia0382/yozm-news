package net.yozmnews.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * 평가기 시간 — 요청 마감 · 판정 한 번의 가장 긴 시간 · 일시 실패 뒤 간격(1분부터 두 배씩, 1시간에서 멈춘다).
 * <p>
 * 지키는 규칙: evaluation-R53
 */
class EvaluatorTimingTests {

	@Test
	void aJudgementWaitsAtMostTwoRequestDeadlines() {
		assertThat(EvaluatorTiming.REQUEST_DEADLINE).isEqualTo(Duration.ofSeconds(15));
		assertThat(EvaluatorTiming.LONGEST_JUDGEMENT).isEqualTo(Duration.ofSeconds(30));
	}

	@Test
	void theRetryDelayDoublesFromOneMinuteAndStopsAtAnHourWithoutOverflowing() {
		assertThat(EvaluatorTiming.retryDelay(1)).isEqualTo(Duration.ofMinutes(1));
		assertThat(EvaluatorTiming.retryDelay(2)).isEqualTo(Duration.ofMinutes(2));
		assertThat(EvaluatorTiming.retryDelay(4)).isEqualTo(Duration.ofMinutes(8));
		assertThat(EvaluatorTiming.retryDelay(6)).isEqualTo(Duration.ofMinutes(32));
		assertThat(EvaluatorTiming.retryDelay(7)).isEqualTo(Duration.ofHours(1));
		assertThat(EvaluatorTiming.retryDelay(8)).isEqualTo(Duration.ofHours(1));
		assertThat(EvaluatorTiming.retryDelay(Integer.MAX_VALUE)).isEqualTo(Duration.ofHours(1));
	}

	@Test
	void transientFailuresStartAtOne() {
		assertThatIllegalArgumentException().isThrownBy(() -> EvaluatorTiming.retryDelay(0));
	}

}
