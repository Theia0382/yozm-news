package net.yozmnews.evaluation;

/**
 * 어긋난 상태 — 지금 태그 기준 · 저장 기준과 그 이력 때문에 태그가 붙지 않을 수 있는 상태 · docs/specs/evaluation.md §3 ·
 * evaluation-R34.
 */
public enum ThresholdCondition {

	/** 태그 기준이 저장 기준보다 낮다 */
	TAG_BELOW_STORE,

	/** 태그 기준이 지난 저장 기준 최댓값보다 낮고, 그 최댓값이 지금 저장 기준보다 높다 */
	TAG_BELOW_PAST_STORE,

	/** 그 버전 · 그 태그 종류의 저장 기준이 바뀐 적이 있다 */
	STORE_THRESHOLD_CHANGED

}
