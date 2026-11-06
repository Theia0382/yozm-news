package net.yozmnews.event;

/**
 * 사건 묶기 채우기의 결과 — docs/specs/event.md §3
 */
public sealed interface IntakeFillResult {

	/**
	 * 채웠다.
	 * @param articles 기간에 처음 본 기사 수
	 * @param enqueued 그 가운데 새로 대기에 넣은 기사 수 — 배정이 있거나 이미 대기에 있는 기사는 세지 않는다
	 */
	record Filled(int articles, int enqueued) implements IntakeFillResult {
	}

	/**
	 * 일수가 1 ~ {@code max} 밖이라 아무것도 하지 않았다.
	 * @param max 상한 — {@link EventIntakeAdministration#MAX_DAYS}
	 */
	record DaysOutOfRange(int max) implements IntakeFillResult {
	}

}
