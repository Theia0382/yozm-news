package net.yozmnews.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 엔트리 개수와 비율 — 비율은 개수로 나누고, 합은 개수를 더한 뒤 나눈다. 기대값은 손으로 셈한 수다.
 * <p>
 * 지키는 규칙: article-R14
 */
class EntryCountsTests {

	@Test
	void ratesDivideEachCountByTheEntries() {
		EntryCounts counts = new EntryCounts(4, 3, 2, 1, 3, 0);

		assertThat(counts.duplicateRate()).isEqualTo(0.75);
		assertThat(counts.estimatedRate()).isEqualTo(0.75);
		assertThat(counts.titleMatchRate()).isEqualTo(0.25);
	}

	@Test
	void noEntriesMeansNoRateRatherThanZero() {
		assertThat(EntryCounts.NONE.duplicateRate()).isNull();
		assertThat(EntryCounts.NONE.estimatedRate()).isNull();
		assertThat(EntryCounts.NONE.titleMatchRate()).isNull();
		assertThat(new EntryCounts(2, 0, 0, 0, 0, 0).duplicateRate()).isEqualTo(0.0);
	}

	@Test
	void aSumAddsTheCountsBeforeDividingNotTheRates() {
		EntryCounts sum = new EntryCounts(10, 1, 1, 0, 0, 0).plus(new EntryCounts(90, 45, 45, 0, 0, 0));

		assertThat(sum).isEqualTo(new EntryCounts(100, 46, 46, 0, 0, 0));
		assertThat(sum.duplicateRate()).isEqualTo(0.46);
	}

	@Test
	void everyCountIsAddedToItsOwnCount() {
		assertThat(new EntryCounts(5, 3, 2, 1, 2, 1).plus(new EntryCounts(7, 4, 3, 1, 6, 3)))
			.isEqualTo(new EntryCounts(12, 7, 5, 2, 8, 4));
	}

	@Test
	void boundaryCountsAreAllowed() {
		assertThat(new EntryCounts(3, 3, 0, 3, 3, 0).duplicateRate()).isEqualTo(1.0);
		assertThat(new EntryCounts(3, 3, 2, 1, 0, 0).urlMatches()).isEqualTo(2);
		assertThat(new EntryCounts(2, 1, 0, 0, 0, 1).entries()).isEqualTo(2);
	}

	@ParameterizedTest
	@CsvSource({ "-1, 0, 0, 0, 0, 0", "2, 3, 0, 0, 0, 0", "2, 0, 0, 0, 3, 0", "3, 1, 0, 2, 0, 0",
			"3, 3, 2, 2, 0, 0", "1, 0, -1, 0, 0, 0", "3, 1, 0, 0, 0, 3", "1, 0, 0, 0, 0, -1" })
	void countsThatCannotHappenAreRejected(int entries, int duplicates, int urlMatches, int titleMatches,
			int estimated, int guidReuses) {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new EntryCounts(entries, duplicates, urlMatches, titleMatches, estimated, guidReuses));
	}

}
