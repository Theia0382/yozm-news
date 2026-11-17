package net.yozmnews.feedback.domain.record;

/**
 * 받을 수 있는 기사의 판정 — 정본. docs/specs/feedback.md §4 · feedback-R2
 */
public final class FeedbackAcceptance {

	private FeedbackAcceptance() {
	}

	/**
	 * 받을 수 있는 기사인지 판정한다 — 기사가 있고 요청의 다이제스트(그 사용자의 것)가 그 기사를 인용할 때만 받는다.
	 * @param articleExists 기사가 있다
	 * @param cited 그 사용자의 그 다이제스트가 그 기사를 인용한다
	 */
	public static boolean accepts(boolean articleExists, boolean cited) {
		return articleExists && cited;
	}

}
