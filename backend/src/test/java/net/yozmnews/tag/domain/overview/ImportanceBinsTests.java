package net.yozmnews.tag.domain.overview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import net.yozmnews.tag.Share;
import net.yozmnews.tag.ValueBucket;
import org.junit.jupiter.api.Test;

/**
 * 중요도 기대값의 칸 — 0.5 폭 8칸의 경계와 끝 포함 마지막 칸, 비율의 분모를 본다.
 * <p>
 * 지키는 규칙: tag-R29
 */
class ImportanceBinsTests {

	@Test
	void anImportanceFallsInTheHighestBucketWhoseLowerEndItReaches() {
		assertThat(ImportanceBins.index(1.0)).isZero();
		assertThat(ImportanceBins.index(1.4999)).isZero();
		assertThat(ImportanceBins.index(1.5)).isEqualTo(1);
		assertThat(ImportanceBins.index(4.5)).isEqualTo(7);
		assertThat(ImportanceBins.index(5.0)).isEqualTo(7);
	}

	@Test
	void anImportanceOutsideOneToFiveIsACallerDefect() {
		assertThatIllegalArgumentException().isThrownBy(() -> ImportanceBins.index(0.99));
		assertThatIllegalArgumentException().isThrownBy(() -> ImportanceBins.index(5.01));
	}

	@Test
	void theBucketsShareTheGivenEvaluationCount() {
		ImportanceBins bins = new ImportanceBins();
		bins.add(1.0);
		bins.add(5.0);

		assertThat(bins.buckets(4)).hasSize(8)
			.startsWith(new ValueBucket(new BigDecimal("1.0"), new BigDecimal("1.5"), false, new Share(1, 0.25)))
			.endsWith(new ValueBucket(new BigDecimal("4.5"), new BigDecimal("5.0"), true, new Share(1, 0.25)));
		assertThat(bins.buckets(4).get(1).share()).isEqualTo(new Share(0, 0.0));
		assertThat(new ImportanceBins().buckets(0).getFirst().share()).isEqualTo(new Share(0, null));
	}

}
