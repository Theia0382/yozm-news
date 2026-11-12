package net.yozmnews.event.domain.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import net.yozmnews.event.AssignmentCriteria;
import net.yozmnews.event.EventStatus;
import net.yozmnews.event.domain.event.LockedEvent;
import org.junit.jupiter.api.Test;

/**
 * 사건 대 사건 규칙 — 핵심 말 겹침의 기준 쪽(기사 수가 적은 쪽, 같으면 나중에 연 쪽), 남는 사건(기사 수가 많은 쪽, 같으면 먼저 연 쪽),
 * 병합이 쓰는 앞선 배정 확률(확률 기준 0.50 미만만).
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

	@Test
	void aPriorProbabilityCountsOnlyBelowTheProbabilityThreshold() {
		AssignmentCriteria criteria = new AssignmentCriteria(Duration.ofHours(72), 0.80, 0.55, 0.40, 0.50);
		assertThat(MergeRule.priorProbability(null, criteria)).isNull();
		assertThat(MergeRule.priorProbability(0.4999, criteria)).isEqualTo(0.4999);
		assertThat(MergeRule.priorProbability(0.50, criteria)).isNull();
		assertThat(MergeRule.priorProbability(0.8, criteria)).isNull();
	}

	private static LockedEvent event(long id, int count) {
		return new LockedEvent(id, EventStatus.OPEN, "model-a", new float[] { 1, 0 }, count, T, T, T, T);
	}

}
