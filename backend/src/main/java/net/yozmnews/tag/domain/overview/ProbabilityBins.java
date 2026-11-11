package net.yozmnews.tag.domain.overview;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.yozmnews.tag.ProbabilityBucket;
import net.yozmnews.tag.ProbabilityHistogram;
import net.yozmnews.tag.StoredRange;
import net.yozmnews.tag.domain.result.Probabilities;
import org.jspecify.annotations.Nullable;

/**
 * 확률 칸 — 0.1 폭 10칸 {@code [0, 0.1)} … {@code [0.9, 1.0]}(마지막 칸만 끝 포함)의 칸 고르기 · 저장된 범위 판정 · 세기 · 칸 규칙의 정본 ·
 * docs/specs/tag.md §5.
 * <p>
 * 칸 고르기는 {@link Probabilities#atLeast} 로 칸의 아래 끝과 비교한다 — 저장 기준 거르기 · 판정과 같은 십진 비교다.
 */
public final class ProbabilityBins {

	/** 칸 수 */
	public static final int COUNT = 10;

	private final long[] counts = new long[COUNT];

	/** 그 확률을 센다 */
	public void add(double probability) {
		this.counts[index(probability)]++;
	}

	/**
	 * 센 칸.
	 * @param storeThreshold 지금 저장 기준 — 없으면(형식) 모든 칸이 {@link StoredRange#ALL}
	 */
	public ProbabilityHistogram histogram(@Nullable BigDecimal storeThreshold, boolean storeThresholdChanged) {
		List<ProbabilityBucket> buckets = new ArrayList<>(COUNT);
		for (int i = 0; i < COUNT; i++) {
			buckets.add(new ProbabilityBucket(lower(i), upper(i), last(i), this.counts[i],
					(storeThreshold == null) ? StoredRange.ALL : stored(i, storeThreshold)));
		}
		return new ProbabilityHistogram(storeThreshold, storeThresholdChanged, buckets);
	}

	/**
	 * 확률이 드는 칸 — 아래 끝이 확률 이하인 가장 높은 칸.
	 * @throws IllegalArgumentException 확률이 0 ~ 1 밖일 때
	 */
	static int index(double probability) {
		if (!(probability >= 0 && probability <= 1)) {
			throw new IllegalArgumentException("확률은 0 ~ 1 이다: " + probability);
		}
		for (int i = COUNT - 1; i > 0; i--) {
			if (Probabilities.atLeast(probability, lower(i))) {
				return i;
			}
		}
		return 0;
	}

	/**
	 * 칸이 지금 저장 기준 이상인가 — 위 끝이 저장 기준 이하면(끝을 포함하는 마지막 칸은 저장 기준 미만이면) {@link StoredRange#NONE}, 아래 끝이
	 * 저장 기준 이상이면 {@link StoredRange#ALL}, 그 밖은 {@link StoredRange#PART}.
	 */
	static StoredRange stored(int index, BigDecimal storeThreshold) {
		Objects.requireNonNull(storeThreshold, "storeThreshold");
		int upper = upper(index).compareTo(storeThreshold);
		if (last(index) ? upper < 0 : upper <= 0) {
			return StoredRange.NONE;
		}
		if (lower(index).compareTo(storeThreshold) >= 0) {
			return StoredRange.ALL;
		}
		return StoredRange.PART;
	}

	private static BigDecimal lower(int index) {
		return BigDecimal.valueOf(index, 1);
	}

	private static BigDecimal upper(int index) {
		return BigDecimal.valueOf(index + 1, 1);
	}

	private static boolean last(int index) {
		return index == COUNT - 1;
	}

}
