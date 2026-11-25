package net.yozmnews.report;

/**
 * 리포트 섹션의 종류 — 개요와 갈래 셋 · docs/specs/report.md §3 · report-R5 · report-R14
 */
public enum ReportSectionKind {

	/** 개요 — 출력의 섹션이고 입력 갈래가 아니다 */
	OVERVIEW,

	/** 이번 기간 추적한 사용자 스토리라인의 변화 */
	STORYLINES,

	/** 구독 주제의 보도량과 직전 기간 비교 */
	COVERAGE,

	/** 관심도의 변화, 새로 생긴 · 사라진 관심 */
	INTERESTS

}
