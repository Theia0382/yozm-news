package net.yozmnews.report.domain.material;

import org.jspecify.annotations.Nullable;

/**
 * 보도량 판정과 그 상수의 정본 · docs/specs/report.md §5 · report-R15
 * <p>
 * 이번 수와 직전 수로 이 순서로 본다 — 직전이 없으면 비교 없음, 차이가 {@link #MIN_CHANGE} 보다 작으면 그대로, 직전이 0 이면 새로,
 * 이번 ≥ 직전 × {@link #RISE_RATIO} 면 늘었다, 이번 ≤ 직전 × {@link #FALL_RATIO} 면 줄었다, 그 밖은 그대로. 상수는 측정 전 목표다.
 */
public final class CoverageTrend {

	/** 판정에 드는 차이의 하한(기사 수) — 측정 전 목표 */
	public static final long MIN_CHANGE = 3;

	/** 늘었다고 볼 비율 — 측정 전 목표 */
	public static final double RISE_RATIO = 1.5;

	/** 줄었다고 볼 비율 — 측정 전 목표 */
	public static final double FALL_RATIO = 0.5;

	private CoverageTrend() {
	}

	/** 보도량의 변화 */
	public enum Trend {

		/** 직전이 0 이고 이번에 생겼다 */
		NEW,

		/** 늘었다 */
		ROSE,

		/** 줄었다 */
		FELL,

		/** 그대로 */
		STEADY,

		/** 직전 값이 없다 */
		NO_COMPARISON

	}

	/**
	 * @param current 이번 기간 기사 수
	 * @param previous 직전 기간 기사 수 — 없으면 null
	 */
	public static Trend of(long current, @Nullable Long previous) {
		if (previous == null) {
			return Trend.NO_COMPARISON;
		}
		if (Math.abs(current - previous) < MIN_CHANGE) {
			return Trend.STEADY;
		}
		if (previous == 0) {
			return Trend.NEW;
		}
		if (current >= previous * RISE_RATIO) {
			return Trend.ROSE;
		}
		if (current <= previous * FALL_RATIO) {
			return Trend.FELL;
		}
		return Trend.STEADY;
	}

}
