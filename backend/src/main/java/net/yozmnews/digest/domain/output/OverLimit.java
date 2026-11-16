package net.yozmnews.digest.domain.output;

/**
 * 문자열이 상한을 넘을 때의 처분 · docs/specs/digest.md §5 · digest-R5
 */
public enum OverLimit {

	/** 출력 전체를 읽지 못한 것으로 한다 */
	UNREADABLE,

	/** 그 문자열을 가진 배열 원소를 버린다 */
	DROP_ITEM,

	/** 그 값만 지운다(없음으로) — 선택 속성에만 쓴다 */
	DROP_VALUE

}
