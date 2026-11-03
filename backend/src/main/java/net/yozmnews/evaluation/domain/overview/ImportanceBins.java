package net.yozmnews.evaluation.domain.overview;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import net.yozmnews.evaluation.ValueBucket;

/**
 * 중요도 기대값의 칸 — 0.5 폭 8칸 {@code [1, 1.5)} … {@code [4.5, 5]}(마지막 칸만 끝 포함)의 칸 고르기 · 세기 · 칸 규칙의 정본 ·
 * docs/specs/evaluation.md §5
 */
public final class ImportanceBins {

	/** 칸 수 */
	public static final int COUNT = 8;

	private static final BigDecimal LOWEST = BigDecimal.ONE;

	private static final BigDecimal WIDTH = new BigDecimal("0.5");

	private final long[] counts = new long[COUNT];

	/** 그 기대값을 센다 */
	public void add(double importance) {
		this.counts[index(importance)]++;
	}

	/**
	 * 센 칸.
	 * @param evaluations 비율의 분모 — 기대값이 없는 평가를 포함한 평가 수
	 */
	public List<ValueBucket> buckets(long evaluations) {
		List<ValueBucket> buckets = new ArrayList<>(COUNT);
		for (int i = 0; i < COUNT; i++) {
			buckets.add(new ValueBucket(lower(i), lower(i + 1), i == COUNT - 1, Shares.of(this.counts[i], evaluations)));
		}
		return buckets;
	}

	/**
	 * 기대값이 드는 칸 — 아래 끝이 기대값 이하인 가장 높은 칸. 칸의 끝은 2 진수로 정확한 값이라 그대로 비교한다.
	 * @throws IllegalArgumentException 기대값이 1 ~ 5 밖일 때
	 */
	static int index(double importance) {
		if (!(importance >= LOWEST.doubleValue() && importance <= lower(COUNT).doubleValue())) {
			throw new IllegalArgumentException("중요도 기대값은 1 ~ 5 다: " + importance);
		}
		for (int i = COUNT - 1; i > 0; i--) {
			if (importance >= lower(i).doubleValue()) {
				return i;
			}
		}
		return 0;
	}

	private static BigDecimal lower(int index) {
		return LOWEST.add(WIDTH.multiply(BigDecimal.valueOf(index)));
	}

}
