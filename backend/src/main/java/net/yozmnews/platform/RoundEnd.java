package net.yozmnews.platform;

/**
 * 회차가 끝난 까닭 — 회차 기록의 값 · docs/specs/platform.md §3 · 반복 회차.
 */
public enum RoundEnd {

	/** 가져올 대기가 없었다 */
	DRAINED,

	/** 시간 예산을 다 써 새 뭉치를 가져오지 않았다 */
	BUDGET,

	/** 일시 실패가 난 뭉치 뒤에 끝냈다 */
	BACKED_OFF,

	/** 회차를 멈추는 경우로 끝났다 */
	STOPPED,

	/** 뭉치가 예외를 던졌다 */
	FAILED

}
