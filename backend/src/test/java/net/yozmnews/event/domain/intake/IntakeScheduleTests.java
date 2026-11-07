package net.yozmnews.event.domain.intake;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 사건 묶기 대기의 거부 상한 판정을 본다.
 * <p>
 * 지키는 규칙: event-R6
 */
class IntakeScheduleTests {

	@Test
	void rejectionsAreExhaustedAtThree() {
		assertThat(IntakeSchedule.rejectionsExhausted(2)).isFalse();
		assertThat(IntakeSchedule.rejectionsExhausted(3)).isTrue();
	}

}
