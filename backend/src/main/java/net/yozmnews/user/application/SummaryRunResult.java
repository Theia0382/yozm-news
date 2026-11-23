package net.yozmnews.user.application;

/**
 * 정리 실행 하나가 한 일 — 관측과 시험이 읽는다. docs/specs/user.md §4
 *
 * @param stored 저장한 정리 수(새로 만든 것 · 다시 만든 것 · 고친 것)
 * @param removed 지운 정리 수
 * @param failed 실패한 호출 수
 * @param discarded 답은 받았지만 그 사이 개체가 달라져 버린 호출 수
 */
public record SummaryRunResult(int stored, int removed, int failed, int discarded) {

	/** 아무 일도 하지 않은 실행 */
	public static final SummaryRunResult NOTHING = new SummaryRunResult(0, 0, 0, 0);

}
