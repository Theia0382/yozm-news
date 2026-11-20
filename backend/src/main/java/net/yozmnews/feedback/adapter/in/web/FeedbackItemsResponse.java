package net.yozmnews.feedback.adapter.in.web;

import java.util.List;
import java.util.Map;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.FeedbackValue;

/**
 * 항목 기록 — 그 다이제스트에서 값을 누른 항목, 항목 키 순. docs/specs/feedback.md §3 · feedback-R7
 *
 * @param items 값을 누른 항목
 */
record FeedbackItemsResponse(List<FeedbackItemState> items) {

	FeedbackItemsResponse {
		items = List.copyOf(items);
	}

	static FeedbackItemsResponse of(Map<EventId, FeedbackValue> items) {
		return new FeedbackItemsResponse(items.entrySet()
			.stream()
			.map(item -> FeedbackItemState.of(item.getKey().value(), item.getValue()))
			.toList());
	}

}
