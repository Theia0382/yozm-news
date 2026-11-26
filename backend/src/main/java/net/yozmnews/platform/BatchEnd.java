package net.yozmnews.platform;

/**
 * 뭉치 하나가 끝난 꼴 — 회차가 다음 뭉치를 가져올지의 입력 · docs/specs/platform.md §3 · 반복 회차.
 */
public enum BatchEnd {

	/** 멈춤 · 일시 실패 없이 뭉치 상한만큼 가져와 마쳤다 — 대기가 더 있을 수 있다 */
	FULL,

	/** 멈춤 · 일시 실패 없이 마쳤고 상한보다 적게 가져왔다 — 가져올 대기가 없다 */
	DRAINED,

	/** 일시 실패가 하나라도 났다 — 외부 호출 한도에 거듭 부딪히지 않게 회차를 끝낸다 */
	BACKED_OFF,

	/**
	 * 회차를 멈추는 경우로 끝났다 — 필요한 외부 장치를 쓸 수 없거나 뭉치가 예상 못한 예외를 잡아 멈춘 경우처럼 뭉치가 정한다. 뭉치 밖으로 던진 예외는
	 * 이 값이 아니라 회차 기록의 {@link RoundEnd#FAILED} 다
	 */
	STOPPED

}
