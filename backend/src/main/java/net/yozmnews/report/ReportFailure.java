package net.yozmnews.report;

/**
 * 리포트 실패 코드 · docs/specs/report.md §3 · report-R8 · report-R13
 */
public enum ReportFailure {

	/** 생성기가 없다 — profile {@code local} 밖에서 실제 생성기가 서기 전 */
	NO_GENERATOR,

	/** 끝까지 쓴 응답을 서술대로 읽지 못했다 */
	UNREADABLE_OUTPUT,

	/** 응답이 출력 토큰 상한에 닿아 잘렸다 */
	OUTPUT_TRUNCATED,

	/** 모델이 출력을 거절했다 */
	OUTPUT_REFUSED

}
