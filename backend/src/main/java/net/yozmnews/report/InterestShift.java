package net.yozmnews.report;

/**
 * 관심 줄의 변화 — 기간 끝 점이 시작 점 대비 · docs/specs/report.md §3 · report-R17
 */
public enum InterestShift {

	/** 새로 생긴 관심(주제) · 보이게 된 사용자 스토리라인 */
	ENTERED,

	/** 사라진 관심(주제) · 보이지 않게 된 사용자 스토리라인 */
	LEFT,

	/** 변화 없음 */
	NONE

}
