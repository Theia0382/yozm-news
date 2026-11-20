package net.yozmnews.digest.domain.output;

/**
 * 문자열이 상한을 넘을 때의 처분 · docs/specs/digest.md §5 · digest-R5
 */
public enum OverLimit {

	/** 출력 전체를 읽지 못한 것으로 한다 */
	UNREADABLE,

	/** 그 문자열을 가진 항목 칸을 버린다 — 그 항목은 출력에 없는 것이 된다 */
	DROP_ITEM,

	/** 그 값만 지운다(없음으로) — 선택 속성에만 쓴다 */
	DROP_VALUE

}
