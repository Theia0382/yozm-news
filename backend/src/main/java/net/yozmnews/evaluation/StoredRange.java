package net.yozmnews.evaluation;

/**
 * 확률 칸 하나가 지금 저장 기준 이상인가 — 저장 기준 아래 확률은 평가 때 저장하지 않는다 · docs/specs/evaluation.md §3
 */
public enum StoredRange {

	/** 칸 전체가 저장 기준 이상이다 */
	ALL,

	/** 칸이 저장 기준에 걸친다 — 일부만 저장된다 */
	PART,

	/** 칸 전체가 저장 기준 아래다 — 저장하지 않는다 */
	NONE

}
