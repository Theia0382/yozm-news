package net.yozmnews.tag;

/**
 * 어긋난 상태 — 지금 태그 기준 · 저장 기준과 그 이력 · 끝난 채움 때문에 태그가 붙지 않을 수 있는 상태 · docs/specs/tag.md §3 ·
 * tag-R36.
 */
public enum ThresholdCondition {

	/** 태그 기준이 저장 기준보다 낮다 */
	TAG_BELOW_STORE,

	/** 태그 기준이 지난 저장 기준 최댓값보다 낮고, 그 최댓값이 지금 저장 기준보다 높다 */
	TAG_BELOW_PAST_STORE,

	/** 버전 전체에서 저장 바닥이 둘 이상이다 — 평가마다 저장된 확률 범위가 다르다(tag-R32) */
	STORE_THRESHOLD_CHANGED

}
