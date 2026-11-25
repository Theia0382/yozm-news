package net.yozmnews.user;

/**
 * 실패한 변화 리포트의 종류 — {@link ReportFailed} 가 싣는다. 리포트의 기간 종류(주간 · 월간)를 받는 쪽인 이 모듈이 선언한다. docs/specs/user.md
 * §3
 */
public enum ReportFailedKind {

	/** 주간 리포트 */
	WEEK,

	/** 월간 리포트 */
	MONTH

}
