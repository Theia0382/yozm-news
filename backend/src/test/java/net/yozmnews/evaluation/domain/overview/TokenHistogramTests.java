package net.yozmnews.evaluation.domain.overview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import net.yozmnews.evaluation.TokenBucket;
import net.yozmnews.evaluation.TokenSummary;
import org.junit.jupiter.api.Test;

/**
 * 입력 토큰의 칸 — 폭 사다리에서 12칸 이하가 되는 가장 작은 폭, 사다리를 넘는 범위, 평균 · 최소 · 최대, 빈 입력을 본다.
 * <p>
 * 지키는 규칙: evaluation-R29
 */
class TokenHistogramTests {

	@Test
	void theSmallestWidthWithAtMostTwelveBucketsIsChosen() {
		TokenSummary summary = summary(3_012, 3_999, 4_100);

		assertThat(summary.buckets()).hasSize(12);
		assertThat(summary.buckets().getFirst()).isEqualTo(new TokenBucket(3_000, 3_100, 1));
		assertThat(summary.buckets().get(9)).isEqualTo(new TokenBucket(3_900, 4_000, 1));
		assertThat(summary.buckets().get(10)).isEqualTo(new TokenBucket(4_000, 4_100, 0));
		assertThat(summary.buckets().getLast()).isEqualTo(new TokenBucket(4_100, 4_200, 1));
		assertThat(summary.mean()).isEqualTo(3_703.6666666666665);
		assertThat(summary.min()).isEqualTo(3_012);
		assertThat(summary.max()).isEqualTo(4_100);
	}

	@Test
	void aWiderRangeTakesAWiderRung() {
		TokenSummary summary = summary(100, 9_900);

		assertThat(summary.buckets()).hasSize(10)
			.startsWith(new TokenBucket(0, 1_000, 1))
			.endsWith(new TokenBucket(9_000, 10_000, 1));
	}

	@Test
	void aRungThatIsNotAMultipleOfTheNextOneStillCountsEveryValueOnce() {
		// 100 이면 13칸 — 250 폭 6칸
		TokenSummary summary = summary(900, 1_200, 1_500, 2_100);

		assertThat(summary.buckets()).containsExactly(new TokenBucket(750, 1_000, 1), new TokenBucket(1_000, 1_250, 1),
				new TokenBucket(1_250, 1_500, 0), new TokenBucket(1_500, 1_750, 1), new TokenBucket(1_750, 2_000, 0),
				new TokenBucket(2_000, 2_250, 1));
	}

	@Test
	void aSingleValueIsOneBucketOfTheSmallestWidth() {
		assertThat(summary(1_234).buckets()).containsExactly(new TokenBucket(1_200, 1_300, 1));
	}

	@Test
	void aRangeBeyondTheLadderUsesTheWidestRungWithoutABucketLimit() {
		TokenSummary summary = summary(0, 200_000);

		assertThat(summary.buckets()).hasSize(21)
			.startsWith(new TokenBucket(0, 10_000, 1))
			.endsWith(new TokenBucket(200_000, 210_000, 1));
	}

	@Test
	void noValuesGiveNoBucketsAndNoStatistics() {
		assertThat(new TokenHistogram().summary()).isEqualTo(new TokenSummary(null, null, null, java.util.List.of()));
	}

	@Test
	void negativeTokensAreACallerDefect() {
		assertThatIllegalArgumentException().isThrownBy(() -> new TokenHistogram().add(-1));
	}

	private static TokenSummary summary(int... tokens) {
		TokenHistogram histogram = new TokenHistogram();
		for (int value : tokens) {
			histogram.add(value);
		}
		return histogram.summary();
	}

}
