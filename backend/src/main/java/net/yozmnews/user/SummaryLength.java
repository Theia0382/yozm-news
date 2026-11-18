package net.yozmnews.user;

/**
 * 다이제스트 요약 길이 — 사용자가 고른 단계. docs/specs/user.md §3 · user-R56
 * <p>
 * 단계의 뜻(주요 뉴스 요약 · 개요의 길이 기준)은 다이제스트의 지시가 정한다 — 이 모듈은 고른 값만 저장한다.
 */
public enum SummaryLength {

	/** 짧게 */
	SHORT,

	/** 보통 */
	NORMAL,

	/** 자세히 */
	DETAILED

}
