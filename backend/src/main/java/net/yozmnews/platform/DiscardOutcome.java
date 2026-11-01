package net.yozmnews.platform;

/**
 * 폐기한 발행 하나의 결과 종류 — docs/specs/platform.md §3.
 */
public enum DiscardOutcome {

	/** 지웠다 */
	DISCARDED,

	/** 남겨진 발행이 아니다 */
	NOT_STRANDED,

	/** 읽은 뒤 다른 요청이 바꿔 지우지 않았다 */
	CHANGED,

	/** 레지스트리에 없다 */
	NOT_FOUND

}
