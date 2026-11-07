package net.yozmnews.event.domain.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import net.yozmnews.event.domain.event.EventStatus;
import net.yozmnews.event.domain.event.LockedEvent;
import org.junit.jupiter.api.Test;

/**
 * 사건 대 사건 규칙 — 핵심 말 겹침의 기준 쪽(기사 수가 적은 쪽, 같으면 나중에 연 쪽)과 남는 사건(기사 수가 많은 쪽, 같으면 먼저 연 쪽).
 * <p>
 * 지키는 규칙: event-R13
 */
class MergeRuleTests {

	private static final Instant T = Instant.parse("2026-11-05T00:00:00Z");

	@Test
	void theSmallerEventsTermsAreTheBaseAndTiesGoToTheLaterOne() {
		assertThat(MergeRule.termsFromSubject(1, 10, 3, 5)).isTrue();
		assertThat(MergeRule.termsFromSubject(3, 10, 1, 5)).isFalse();
		assertThat(MergeRule.termsFromSubject(2, 10, 2, 5)).isTrue();
		assertThat(MergeRule.termsFromSubject(2, 5, 2, 10)).isFalse();
	}

	@Test
	void theLargerEventSurvivesAndTiesGoToTheEarlierOne() {
		assertThat(MergeRule.survivor(event(10, 3), event(5, 1))).isEqualTo(10);
		assertThat(MergeRule.survivor(event(5, 1), event(10, 3))).isEqualTo(10);
		assertThat(MergeRule.survivor(event(10, 2), event(5, 2))).isEqualTo(5);
		assertThat(MergeRule.survivor(event(5, 2), event(10, 2))).isEqualTo(5);
	}

	private static LockedEvent event(long id, int count) {
		return new LockedEvent(id, EventStatus.OPEN, "model-a", new float[] { 1, 0 }, count, T, T, T, T);
	}

}
