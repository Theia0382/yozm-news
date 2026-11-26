package net.yozmnews.report;

/**
 * 리포트 섹션의 종류 — 갈래 둘 · docs/specs/report.md §3 · report-R5 · report-R14
 */
public enum ReportSectionKind {

	/** 이번 기간 추적한 사용자 스토리라인의 변화 */
	STORYLINES,

	/** 관심도의 변화, 새로 생긴 · 사라진 관심 */
	INTERESTS

}
