package net.yozmnews.storyline.domain.big;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import net.yozmnews.event.EventId;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.event.EventTally;
import net.yozmnews.storyline.BigStoryline;
import net.yozmnews.storyline.BigStorylineCriteria;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineState;
import net.yozmnews.storyline.StorylineView;
import org.junit.jupiter.api.Test;

/**
 * 큰 판정 규칙 — 보도 강도(중요도 합 ÷ (소스 수 × 기간 길이(일)))가 기준에 이르러야 하고(경계 포함), 판정은 반올림한 표시값이 아니라 곱으로
 * 견준다. 순서는 보도 강도 내림차순, 스토리라인 ID 오름차순.
 * <p>
 * 지키는 규칙: storyline-R19
 */
class BigStorylineRuleTests {

	private static final BigStorylineCriteria CRITERIA = new BigStorylineCriteria(new BigDecimal("15.00"));

	private static final BigBasis FOUR_SOURCES_ONE_DAY = new BigBasis(4, Duration.ofHours(24));

	@Test
	void theIntensityMustReachTheBoundAndTheBoundIsIncluded() {
		// 60 ÷ (4 × 1) = 15.00 — 경계
		assertThat(BigStorylineRule.isBig(sum("60.00"), FOUR_SOURCES_ONE_DAY, CRITERIA)).isTrue();
		assertThat(BigStorylineRule.intensity(sum("60.00"), FOUR_SOURCES_ONE_DAY)).hasToString("15.00");
		// 59.99 ÷ 4 = 14.9975
		assertThat(BigStorylineRule.isBig(sum("59.99"), FOUR_SOURCES_ONE_DAY, CRITERIA)).isFalse();
		// 소스가 다섯이면 60 ÷ 5 = 12.00
		assertThat(BigStorylineRule.isBig(sum("60.00"), new BigBasis(5, Duration.ofHours(24)), CRITERIA)).isFalse();
		assertThat(BigStorylineRule.intensity(sum("60.00"), new BigBasis(5, Duration.ofHours(24)))).hasToString("12.00");
	}

	@Test
	void theLengthOfThePeriodDividesTheSumByDays() {
		// 이틀에 120 — 120 ÷ (4 × 2) = 15.00
		BigBasis twoDays = new BigBasis(4, Duration.ofHours(48));
		assertThat(BigStorylineRule.isBig(sum("120.00"), twoDays, CRITERIA)).isTrue();
		assertThat(BigStorylineRule.intensity(sum("120.00"), twoDays)).hasToString("15.00");
		assertThat(BigStorylineRule.isBig(sum("60.00"), twoDays, CRITERIA)).isFalse();
		// 23시간인 날 — 60 × 24 ÷ (4 × 23) = 15.652…, 25시간인 날 — 60 × 24 ÷ (4 × 25) = 14.40
		assertThat(BigStorylineRule.intensity(sum("60.00"), new BigBasis(4, Duration.ofHours(23)))).hasToString("15.65");
		assertThat(BigStorylineRule.isBig(sum("60.00"), new BigBasis(4, Duration.ofHours(23)), CRITERIA)).isTrue();
		assertThat(BigStorylineRule.intensity(sum("60.00"), new BigBasis(4, Duration.ofHours(25)))).hasToString("14.40");
		assertThat(BigStorylineRule.isBig(sum("60.00"), new BigBasis(4, Duration.ofHours(25)), CRITERIA)).isFalse();
	}

	@Test
	void theLengthKeepsFractionsOfASecond() {
		// 하루하고 반 초 — 60 × 86400 ÷ (4 × 86400.5) = 14.9999… 표시값은 15.00 이지만 기준 15.00 에 이르지 못한다
		BigBasis dayAndHalfSecond = new BigBasis(4, Duration.ofHours(24).plusMillis(500));
		assertThat(BigStorylineRule.intensity(sum("60.00"), dayAndHalfSecond)).hasToString("15.00");
		assertThat(BigStorylineRule.isBig(sum("60.00"), dayAndHalfSecond, CRITERIA)).isFalse();
		// 반 초인 기간 — 1 × 86400 ÷ (1 × 0.5) = 172800.00
		BigBasis halfSecond = new BigBasis(1, Duration.ofMillis(500));
		assertThat(BigStorylineRule.intensity(sum("1.00"), halfSecond)).hasToString("172800.00");
		assertThat(BigStorylineRule.isBig(sum("1.00"), halfSecond, CRITERIA)).isTrue();
		// 중요도 합이 0 이면 길이가 아무리 짧아도 크지 않다
		assertThat(BigStorylineRule.isBig(new EventTally(9, 4, null, BigDecimal.ZERO), halfSecond, CRITERIA)).isFalse();
	}

	@Test
	void theBasisCarriesTheSourcesOfTheTotalAndTheLengthOfThePeriod() {
		EventPeriod period = new EventPeriod(Instant.parse("2026-11-05T00:00:00Z"), Instant.parse("2026-11-06T12:00:00Z"));

		assertThat(BigBasis.of(new EventTally(30, 7, null, BigDecimal.ZERO), period))
			.isEqualTo(new BigBasis(7, Duration.ofHours(36)));
		assertThatIllegalArgumentException().isThrownBy(() -> new BigBasis(-1, Duration.ofHours(24)));
		assertThatIllegalArgumentException().isThrownBy(() -> new BigBasis(4, Duration.ZERO));
	}

	@Test
	void theJudgementDoesNotUseTheRoundedIntensity() {
		// 59.98 ÷ 4 = 14.995 — 표시값은 15.00 으로 올라가지만 기준 15.00 에 이르지 못한다
		assertThat(BigStorylineRule.intensity(sum("59.98"), FOUR_SOURCES_ONE_DAY)).hasToString("15.00");
		assertThat(BigStorylineRule.isBig(sum("59.98"), FOUR_SOURCES_ONE_DAY, CRITERIA)).isFalse();
	}

	@Test
	void nothingIsBigWithoutSourcesOrImportance() {
		BigBasis noSources = new BigBasis(0, Duration.ofHours(24));
		assertThat(BigStorylineRule.isBig(sum("999.00"), noSources, CRITERIA)).isFalse();
		assertThat(BigStorylineRule.intensity(sum("999.00"), noSources)).hasToString("0.00");
		// 중요도가 있는 기사가 없으면 합이 0 이다
		assertThat(BigStorylineRule.isBig(new EventTally(9, 4, null, BigDecimal.ZERO), FOUR_SOURCES_ONE_DAY, CRITERIA)).isFalse();
		assertThat(BigStorylineRule.intensity(new EventTally(9, 4, null, BigDecimal.ZERO), FOUR_SOURCES_ONE_DAY))
			.hasToString("0.00");
	}

	@Test
	void ordersByHigherIntensityThenLowerId() {
		List<BigStoryline> storylines = new ArrayList<>(List.of(big(9, "15.00"), big(2, "15.00"), big(7, "20.00")));

		storylines.sort(BigStorylineRule.ORDER);

		// 20.00 → 15.00 둘은 ID 가 작은 것부터
		assertThat(storylines).extracting(big -> big.storyline().id().value()).containsExactly(7L, 2L, 9L);
	}

	private static EventTally sum(String importanceSum) {
		return new EventTally(20, 4, new BigDecimal("3.00"), new BigDecimal(importanceSum));
	}

	private static BigStoryline big(long id, String intensity) {
		Instant at = Instant.parse("2026-11-05T00:00:00Z");
		return new BigStoryline(new StorylineView(new StorylineId(id), StorylineState.ACTIVE, 2, 4, at, at,
				new EventId(1), at, null), sum("60.00"), new BigDecimal(intensity));
	}

}
