package net.yozmnews.platform;

/**
 * 다시 처리한 발행 하나의 결과 종류 — docs/specs/platform.md §3.
 */
public enum ResubmissionOutcome {

	/** 재제출했다 — 동시 요청이 먼저 재제출한 경우를 포함한다 */
	RESUBMITTED,

	/** 상한에 이른 발행이 아니다 — 상한 미만 · 처리 중 · 그사이 바뀜 */
	NOT_EXHAUSTED,

	/** 이벤트 클래스를 찾을 수 없어 재제출할 수 없다 — 폐기만 할 수 있다 */
	UNREADABLE,

	/** 레지스트리에 없다 — 이미 완료돼 지워졌거나 폐기됐다 */
	NOT_FOUND

}
