package net.yozmnews.digest;

/**
 * 날짜의 상태 — 그 날짜에 무엇이 보이는가. docs/specs/digest.md §3 · digest-R39
 */
public enum DigestDayState {

	/** 보이는 다이제스트가 있다 */
	CONTENT,

	/** 보이는 다이제스트가 없고 가장 최근 시도가 생성을 기다리거나 생성 중이다 */
	IN_PROGRESS,

	/** 보이는 다이제스트가 없고 가장 최근 시도가 후보 없음으로 끝났다 */
	EMPTY,

	/** 보이는 다이제스트가 없고 가장 최근 시도가 실패했다 */
	FAILED

}
