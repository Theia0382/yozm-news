package net.yozmnews.digest.domain.output;

/**
 * 문자열이 비었을 때(앞뒤 공백을 지운 값이 빈 문자열)의 처분 · docs/specs/digest.md §5 · digest-R4
 */
public enum OnBlank {

	/** 출력 전체를 읽지 못한 것으로 한다 */
	UNREADABLE,

	/** 없음으로 받는다 — 선택 속성에만 쓴다 */
	ABSENT,

	/** 빈 문자열 그대로 받는다 — 뒤의 대조가 처분한다 */
	KEEP

}
