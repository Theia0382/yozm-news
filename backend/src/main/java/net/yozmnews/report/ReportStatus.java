package net.yozmnews.report;

/**
 * 리포트 상태 · docs/specs/report.md §3 · report-R1
 */
public enum ReportStatus {

	/** 생성을 기다림 */
	SCHEDULED,

	/** 생성 중 */
	RUNNING,

	/** 내용 있음 */
	READY,

	/** 재료 없음 — 입력 갈래가 하나도 없어 생성기를 부르지 않았다 */
	EMPTY,

	/** 실패 */
	FAILED;

	/** 끝난 상태인가 — {@code READY} · {@code EMPTY} · {@code FAILED} */
	public boolean finished() {
		return switch (this) {
			case SCHEDULED, RUNNING -> false;
			case READY, EMPTY, FAILED -> true;
		};
	}

}
