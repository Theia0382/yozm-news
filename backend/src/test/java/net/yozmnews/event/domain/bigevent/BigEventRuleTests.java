package net.yozmnews.event.domain.bigevent;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import net.yozmnews.event.BigEvent;
import net.yozmnews.event.BigEventCriteria;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventTally;
import org.junit.jupiter.api.Test;

/**
 * 큰 사건 판정 규칙 — 소스 하한과 중요도 하한을 모두 넘어야 하고(경계 포함), 중요도가 없으면 큰 사건이 아니다. 순서는 소스 수 · 중요도
 * 내림차순, 사건 ID 오름차순.
 * <p>
 * 지키는 규칙: event-R15
 */
class BigEventRuleTests {

	private static final BigEventCriteria CRITERIA = new BigEventCriteria(3, new BigDecimal("3.00"));

	@Test
	void bothBoundsMustBeReachedAndTheBoundsAreIncluded() {
		assertThat(BigEventRule.isBig(new EventTally(3, 3, new BigDecimal("3.00")), CRITERIA)).isTrue();
		assertThat(BigEventRule.isBig(new EventTally(9, 2, new BigDecimal("4.50")), CRITERIA)).isFalse();
		assertThat(BigEventRule.isBig(new EventTally(9, 5, new BigDecimal("2.99")), CRITERIA)).isFalse();
		assertThat(BigEventRule.isBig(new EventTally(9, 5, null), CRITERIA)).isFalse();
	}

	@Test
	void ordersByMoreSourcesThenHigherImportanceThenLowerId() {
		List<BigEvent> events = new ArrayList<>(List.of(big(7, 5, "3.10"), big(9, 5, "3.50"), big(2, 3, "4.00"),
				big(4, 5, "3.50")));

		events.sort(BigEventRule.ORDER);

		assertThat(events).extracting(event -> event.id().value()).containsExactly(4L, 9L, 7L, 2L);
	}

	private static BigEvent big(long id, int sources, String importance) {
		return new BigEvent(new EventId(id), List.of(), new EventTally(sources, sources, new BigDecimal(importance)));
	}

}
