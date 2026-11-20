package net.yozmnews.feedback.domain.record;

import java.util.List;
import java.util.Objects;

import net.yozmnews.event.EventId;

/**
 * 받을 수 있는 항목의 판정 — 정본. docs/specs/feedback.md §4 · feedback-R2
 */
public final class FeedbackAcceptance {

	private FeedbackAcceptance() {
	}

	/**
	 * 받을 수 있는 항목인지 판정한다 — 요청의 다이제스트(그 사용자의 것)에서 요청의 사건이 든 항목을 찾았을 때만 받는다. 항목의 사건 가운데 지금
	 * 없는(지운) 사건이 있어도 받는다 — 그 항목에 누른 사실은 참이고, 없는 사건은 사건 기록에서 빠진다.
	 * @param items 그 항목의 사건들({@link net.yozmnews.feedback.DigestItems#itemOf}) — 찾지 못했으면 빈 목록
	 */
	public static boolean accepts(List<EventId> items) {
		Objects.requireNonNull(items, "items");
		return !items.isEmpty();
	}

}
