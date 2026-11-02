package net.yozmnews.evaluation;

/**
 * 두 기준 변경의 경고 — 이번 변경이 이전 기사와 어긋남을 만들 때 — docs/specs/evaluation.md §3 · evaluation-R12
 */
public enum ThresholdWarning {

	/** 저장 기준을 지금 값보다 낮췄다 — 이전 기사에는 낮춘 구간의 확률이 없다 */
	STORE_THRESHOLD_LOWERED,

	/** 태그 기준을 내렸고 새 값이 그 버전에서 써 온 저장 기준의 최댓값보다 낮다 */
	TAG_BELOW_PAST_STORE,

	/** 이번 변경으로 태그 기준이 저장 기준보다 낮아졌다 */
	TAG_BELOW_STORE

}
