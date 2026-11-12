package net.yozmnews.storyline.domain.big;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import net.yozmnews.event.EventId;
import net.yozmnews.event.EventTally;
import net.yozmnews.storyline.BigStoryline;
import net.yozmnews.storyline.BigStorylineCriteria;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineState;
import net.yozmnews.storyline.StorylineView;
import org.junit.jupiter.api.Test;

/**
 * 큰 판정 규칙 — 소스 기준과 중요도 기준에 모두 이르러야 하고(경계 포함), 중요도 평균이 없으면 큰 스토리라인이 아니다. 순서는 소스 수 · 중요도
 * 평균 내림차순, 스토리라인 ID 오름차순.
 * <p>
 * 지키는 규칙: storyline-R19
 */
class BigStorylineRuleTests {

	private static final BigStorylineCriteria CRITERIA = new BigStorylineCriteria(3, new BigDecimal("3.00"));

	@Test
	void bothBoundsMustBeReachedAndTheBoundsAreIncluded() {
		assertThat(BigStorylineRule.isBig(new EventTally(3, 3, new BigDecimal("3.00")), CRITERIA)).isTrue();
		assertThat(BigStorylineRule.isBig(new EventTally(9, 2, new BigDecimal("4.50")), CRITERIA)).isFalse();
		assertThat(BigStorylineRule.isBig(new EventTally(9, 3, new BigDecimal("2.99")), CRITERIA)).isFalse();
		assertThat(BigStorylineRule.isBig(new EventTally(9, 5, null), CRITERIA)).isFalse();
	}

	@Test
	void ordersByMoreSourcesThenHigherImportanceThenLowerId() {
		List<BigStoryline> storylines = new ArrayList<>(List.of(big(9, 4, "3.50"), big(2, 4, "4.00"), big(7, 5, "3.10"),
				big(4, 4, "3.50")));

		storylines.sort(BigStorylineRule.ORDER);

		// (5, 3.10) → (4, 4.00) → (4, 3.50) 둘은 ID 가 작은 것부터
		assertThat(storylines).extracting(big -> big.storyline().id().value()).containsExactly(7L, 2L, 4L, 9L);
	}

	private static BigStoryline big(long id, int sources, String importance) {
		Instant at = Instant.parse("2026-11-05T00:00:00Z");
		return new BigStoryline(new StorylineView(new StorylineId(id), StorylineState.ACTIVE, 2, sources, at, at,
				new EventId(1), at), new EventTally(sources, sources, new BigDecimal(importance)));
	}

}
