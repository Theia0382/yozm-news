package net.yozmnews.platform;

/**
 * 남겨진 발행의 까닭 — docs/specs/platform.md §3.
 */
public enum StrandedReason {

	/** 실패했고 처리 시도가 상한에 이르렀다 — 다시 처리 · 폐기할 수 있다 */
	EXHAUSTED,

	/** 이벤트 클래스를 찾을 수 없어 재처리가 읽지 못한다 — 폐기만 할 수 있다 */
	UNREADABLE

}
