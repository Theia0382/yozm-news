package net.yozmnews.feedback.adapter.in.web;

import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import net.yozmnews.feedback.FeedbackValue;

/**
 * 현재 상태 — 물은 기사 가운데 현재 상태가 있는 것, 기사 ID 순. docs/specs/feedback.md §3 · feedback-R7
 *
 * @param states 현재 상태가 있는 기사
 */
record FeedbackStatesResponse(List<FeedbackStateItem> states) {

	FeedbackStatesResponse {
		states = List.copyOf(states);
	}

	static FeedbackStatesResponse of(Map<ArticleId, FeedbackValue> states) {
		return new FeedbackStatesResponse(states.entrySet()
			.stream()
			.map(state -> FeedbackStateItem.of(state.getKey().value(), state.getValue()))
			.toList());
	}

}
