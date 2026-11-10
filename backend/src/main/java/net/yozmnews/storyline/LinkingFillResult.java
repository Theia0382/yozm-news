package net.yozmnews.storyline;

/**
 * 스토리라인 잇기 채우기의 결과 — docs/specs/storyline.md §3
 */
public sealed interface LinkingFillResult {

	/**
	 * 채웠다.
	 * @param events 기간의 사건 수
	 * @param enqueued 그 가운데 새로 대기에 넣은 사건 수 — 이미 대기에 있던 사건은 다시 알림만 하고 세지 않는다
	 */
	record Filled(int events, int enqueued) implements LinkingFillResult {
	}

	/**
	 * 일수가 1 ~ {@code max} 밖이라 아무것도 하지 않았다.
	 * @param max 상한 — {@link StorylineLinkingAdministration#MAX_DAYS}
	 */
	record DaysOutOfRange(int max) implements LinkingFillResult {
	}

}
