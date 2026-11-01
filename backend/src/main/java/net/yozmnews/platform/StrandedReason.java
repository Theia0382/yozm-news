package net.yozmnews.platform;

/**
 * 남겨진 발행의 까닭 — docs/specs/platform.md §3.
 */
public enum StrandedReason {

	/** 실패했고 처리 시도가 상한에 이르렀다 — 다시 처리 · 폐기할 수 있다 */
	EXHAUSTED,

	/** 이벤트를 읽을 수 없어(클래스가 없거나 본문이 그 클래스로 읽히지 않음) 재처리가 다루지 못한다 — 폐기만 할 수 있다 */
	UNREADABLE

}
