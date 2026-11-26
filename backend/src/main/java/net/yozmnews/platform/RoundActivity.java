package net.yozmnews.platform;

/**
 * 회차 기록 조회 — docs/specs/platform.md §3 · 반복 회차.
 */
public interface RoundActivity {

	/**
	 * 최근 창(1시간) 안에 끝난 회차의 합과 마지막으로 끝난 회차.
	 * @param round 회차 이름
	 * @throws IllegalArgumentException 회차 이름의 꼴이 틀렸을 때
	 */
	RoundSummary recent(String round);

}
