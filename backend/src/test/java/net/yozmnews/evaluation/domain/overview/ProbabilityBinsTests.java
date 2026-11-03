package net.yozmnews.evaluation.domain.overview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import net.yozmnews.evaluation.ProbabilityBucket;
import net.yozmnews.evaluation.ProbabilityHistogram;
import net.yozmnews.evaluation.StoredRange;
import org.junit.jupiter.api.Test;

/**
 * 확률 칸 — 칸 고르기의 십진 경계, 끝 포함 마지막 칸, 지금 저장 기준에 대한 저장된 범위를 본다.
 * <p>
 * 지키는 규칙: evaluation-R29
 */
class ProbabilityBinsTests {

	@Test
	void aProbabilityFallsInTheHighestBucketWhoseLowerEndItReaches() {
		assertThat(ProbabilityBins.index(0.0)).isZero();
		assertThat(ProbabilityBins.index(0.0999)).isZero();
		assertThat(ProbabilityBins.index(0.1)).isEqualTo(1);
		// 0.3 · 0.7 의 2 진 근삿값은 그 값보다 조금 작지만 십진 표현으로 비교해 그 칸에 든다
		assertThat(ProbabilityBins.index(0.3)).isEqualTo(3);
		assertThat(ProbabilityBins.index(0.7)).isEqualTo(7);
		assertThat(ProbabilityBins.index(0.9)).isEqualTo(9);
		assertThat(ProbabilityBins.index(1.0)).isEqualTo(9);
	}

	@Test
	void aProbabilityOutsideZeroToOneIsACallerDefect() {
		assertThatIllegalArgumentException().isThrownBy(() -> ProbabilityBins.index(-0.01));
		assertThatIllegalArgumentException().isThrownBy(() -> ProbabilityBins.index(1.01));
		assertThatIllegalArgumentException().isThrownBy(() -> ProbabilityBins.index(Double.NaN));
	}

	@Test
	void aBucketBelowTheStoreThresholdIsNotStoredAndOneAcrossItIsPartlyStored() {
		BigDecimal store = new BigDecimal("0.15");

		assertThat(ProbabilityBins.stored(0, store)).isEqualTo(StoredRange.NONE);
		assertThat(ProbabilityBins.stored(1, store)).isEqualTo(StoredRange.PART);
		assertThat(ProbabilityBins.stored(2, store)).isEqualTo(StoredRange.ALL);
		assertThat(ProbabilityBins.stored(9, store)).isEqualTo(StoredRange.ALL);
	}

	@Test
	void aStoreThresholdOnABucketEdgeLeavesNoPartialBucket() {
		BigDecimal store = new BigDecimal("0.1");

		assertThat(ProbabilityBins.stored(0, store)).isEqualTo(StoredRange.NONE);
		assertThat(ProbabilityBins.stored(1, store)).isEqualTo(StoredRange.ALL);
	}

	@Test
	void theInclusiveLastBucketIsPartlyStoredWhenTheStoreThresholdIsOne() {
		BigDecimal store = new BigDecimal("1.0");

		assertThat(ProbabilityBins.stored(8, store)).isEqualTo(StoredRange.NONE);
		// 확률 1.0 은 저장 기준 1.0 이상이라 저장된다
		assertThat(ProbabilityBins.stored(9, store)).isEqualTo(StoredRange.PART);
	}

	@Test
	void theHistogramHasTenBucketsAndWithoutAStoreThresholdEveryBucketIsStored() {
		ProbabilityBins bins = new ProbabilityBins();
		bins.add(0.05);
		bins.add(0.95);
		bins.add(1.0);

		ProbabilityHistogram histogram = bins.histogram(null, false);

		assertThat(histogram.storeThreshold()).isNull();
		assertThat(histogram.buckets()).hasSize(10).allMatch(bucket -> bucket.stored() == StoredRange.ALL);
		assertThat(histogram.buckets().getFirst())
			.isEqualTo(new ProbabilityBucket(new BigDecimal("0.0"), new BigDecimal("0.1"), false, 1, StoredRange.ALL));
		assertThat(histogram.buckets().getLast())
			.isEqualTo(new ProbabilityBucket(new BigDecimal("0.9"), new BigDecimal("1.0"), true, 2, StoredRange.ALL));
		assertThat(histogram.buckets().stream().mapToLong(ProbabilityBucket::count).toArray()).containsExactly(1, 0, 0, 0, 0, 0,
				0, 0, 0, 2);
	}

	@Test
	void theHistogramCarriesTheStoreThresholdAndWhetherItChanged() {
		ProbabilityHistogram histogram = new ProbabilityBins().histogram(new BigDecimal("0.15"), true);

		assertThat(histogram.storeThreshold()).isEqualByComparingTo("0.15");
		assertThat(histogram.storeThresholdChanged()).isTrue();
		assertThat(histogram.buckets().stream().map(ProbabilityBucket::stored)).containsExactly(StoredRange.NONE,
				StoredRange.PART, StoredRange.ALL, StoredRange.ALL, StoredRange.ALL, StoredRange.ALL, StoredRange.ALL,
				StoredRange.ALL, StoredRange.ALL, StoredRange.ALL);
	}

}
