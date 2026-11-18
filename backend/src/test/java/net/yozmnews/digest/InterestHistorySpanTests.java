package net.yozmnews.digest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 관심사 히스토리의 기간 선택지와 단위별 기본값.
 */
class InterestHistorySpanTests {

	@Test
	void eachUnitHasItsDefaultAndTheFirstScreenShowsThirteenWeeks() {
		assertThat(InterestHistorySpan.DEFAULT).isEqualTo(InterestHistorySpan.WEEKS_13);
		assertThat(InterestHistorySpan.defaultOf(InterestHistorySpan.Unit.WEEK)).isEqualTo(InterestHistorySpan.WEEKS_13);
		assertThat(InterestHistorySpan.defaultOf(InterestHistorySpan.Unit.MONTH))
			.isEqualTo(InterestHistorySpan.MONTHS_12);
	}

	@Test
	void theChoicesAreThirteenTwentySixFiftyTwoWeeksAndTwelveTwentyFourThirtySixMonths() {
		assertThat(InterestHistorySpan.values()).extracting(span -> span.unit() + " " + span.count())
			.containsExactly("WEEK 13", "WEEK 26", "WEEK 52", "MONTH 12", "MONTH 24", "MONTH 36");
	}

}
