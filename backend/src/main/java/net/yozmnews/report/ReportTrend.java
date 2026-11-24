package net.yozmnews.report;

/**
 * 보도량의 변화 — 이번 기간 수를 이은 직전 값과 견준 판정 · docs/specs/report.md §3 · report-R15
 * <p>
 * 판정 규칙과 상수는 {@code domain/material/CoverageTrend} 에 있다.
 */
public enum ReportTrend {

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
